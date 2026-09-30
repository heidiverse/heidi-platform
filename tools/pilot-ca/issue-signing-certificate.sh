#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
# SPDX-License-Identifier: Apache-2.0

#
# Issues an issuer signing certificate from a certificate authority you run yourself.
#
# For pilot deployments only, where no Trusted List and no registrar exist yet. The platform
# deliberately does not do this: an issuer signing certificate has to chain to a CA published on a
# Trusted List, and a CA this script mints is on no list. Running it here rather than inside the
# platform keeps that visible, and keeps the CA private key on the operator's machine instead of in
# the platform's database.
#
# The signer's private key never leaves the platform. Export only its public key, sign that, and
# upload the resulting chain back.
#
#   1. Cockpit -> organisation -> signers -> export the signer key's public key as PEM
#   2. ./issue-signing-certificate.sh --public-key signer.pem --common-name "Acme Issuer" \
#          --dns-name issuer.acme.example --dns-name verifier.acme.example
#   3. Cockpit -> trust-system setup (EUDI) -> paste out/leaf.pem as the leaf signing certificate
#      and out/root-ca.pem as the root, then "Upload leaf-to-root trust chain"
#   4. Same dialog -> paste out/root-ca.pem into "Verification root CAs", so the verifier accepts
#      credentials signed under it
#
# Re-running with the same --ca-dir reuses the CA and issues another leaf under it. Delete the
# directory to start over — every certificate issued under the old CA stops verifying.

set -Eeuo pipefail

ca_dir="${PILOT_CA_DIR:-.local/pilot-ca}"
out_dir="${PILOT_CA_OUT_DIR:-}"
public_key=""
common_name=""
dns_names=()
organisation=""
country="CH"
leaf_days="${PILOT_CA_LEAF_DAYS:-365}"
ca_days="${PILOT_CA_DAYS:-1095}"

usage() {
    sed -n '3,23p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
    cat <<'EOF'

Options:
  --public-key PATH    PEM-encoded public key of the signer key (required)
  --common-name NAME   Subject common name of the leaf certificate (required)
  --dns-name HOST      DNS subject alternative name; repeatable. A key the verifier signs
                       x509_san_dns requests with must name the verifier's host.
  --organisation NAME  Subject organisation (default: the common name)
  --country CC         Subject country code (default: CH)
  --ca-dir DIR         Where the CA key and certificate live (default: .local/pilot-ca)
  --out-dir DIR        Where the issued certificates are written (default: <ca-dir>/out)
  --leaf-days N        Leaf validity in days (default: 365)
  --ca-days N          CA validity in days, used only when creating the CA (default: 1095)
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --public-key) public_key="$2"; shift 2 ;;
        --common-name) common_name="$2"; shift 2 ;;
        --dns-name) dns_names+=("DNS:$2"); shift 2 ;;
        --organisation) organisation="$2"; shift 2 ;;
        --country) country="$2"; shift 2 ;;
        --ca-dir) ca_dir="$2"; shift 2 ;;
        --out-dir) out_dir="$2"; shift 2 ;;
        --leaf-days) leaf_days="$2"; shift 2 ;;
        --ca-days) ca_days="$2"; shift 2 ;;
        -h|--help) usage; exit 0 ;;
        *) echo "unknown option: $1" >&2; usage >&2; exit 2 ;;
    esac
done

[[ -n "$public_key" ]] || { echo "--public-key is required" >&2; exit 2; }
[[ -n "$common_name" ]] || { echo "--common-name is required" >&2; exit 2; }
[[ -f "$public_key" ]] || { echo "no such file: $public_key" >&2; exit 2; }
organisation="${organisation:-$common_name}"
# Defaulted after parsing so --out-dir and --ca-dir are order-independent.
out_dir="${out_dir:-$ca_dir/out}"

mkdir -p "$ca_dir" "$out_dir"
chmod 700 "$ca_dir"

ca_key="$ca_dir/root-ca.key"
ca_cert="$ca_dir/root-ca.pem"

# The CA is created once and reused. Regenerating it would invalidate every credential already
# issued under it, which is exactly the failure mode a per-boot CA produces.
if [[ ! -f "$ca_key" || ! -f "$ca_cert" ]]; then
    echo "Creating a new pilot CA in $ca_dir (valid $ca_days days)"
    openssl ecparam -genkey -name prime256v1 -noout -out "$ca_key"
    chmod 600 "$ca_key"
    openssl req -x509 -new -key "$ca_key" -sha256 -days "$ca_days" \
        -subj "/CN=${organisation} Pilot Issuance CA/O=${organisation}/C=${country}" \
        -addext "basicConstraints=critical,CA:TRUE" \
        -addext "keyUsage=critical,keyCertSign,cRLSign" \
        -out "$ca_cert"
else
    echo "Reusing the pilot CA in $ca_dir"
fi

leaf="$out_dir/leaf.pem"
chain="$out_dir/certificate-chain.pem"

# -force_pubkey is what lets us certify a key we do not hold the private half of: the subject key
# comes from the exported public key, and only the CA signs.
ext_file="$(mktemp)"
trap 'rm -f "$ext_file"' EXIT
cat >"$ext_file" <<EOF
basicConstraints=critical,CA:FALSE
keyUsage=critical,digitalSignature
subjectKeyIdentifier=hash
authorityKeyIdentifier=keyid,issuer
EOF
if [[ ${#dns_names[@]} -gt 0 ]]; then
    (IFS=,; echo "subjectAltName=${dns_names[*]}") >>"$ext_file"
fi

openssl x509 -new -force_pubkey "$public_key" \
    -subj "/CN=${common_name}/O=${organisation}/C=${country}" \
    -CA "$ca_cert" -CAkey "$ca_key" -sha256 -days "$leaf_days" \
    -extfile "$ext_file" -out "$leaf"

cat "$leaf" "$ca_cert" >"$chain"
cp "$ca_cert" "$out_dir/root-ca.pem"

# Fail loudly rather than hand over a chain the platform will reject on upload.
openssl verify -CAfile "$ca_cert" "$leaf" >/dev/null
leaf_key="$(openssl x509 -in "$leaf" -noout -pubkey)"
given_key="$(openssl pkey -pubin -in "$public_key" -pubout)"
[[ "$leaf_key" == "$given_key" ]] || {
    echo "leaf public key does not match $public_key" >&2
    exit 1
}

cat <<EOF

Issued a signing certificate valid $leaf_days days.

  leaf          $leaf
  root CA       $out_dir/root-ca.pem
  full chain    $chain

  CA key        $ca_key   <- keep this out of the repository and off shared drives

Upload the leaf and the root as the signer key's certificate chain, then add the root to the
issuer's verification trust anchors. This CA is on no Trusted List: wallets outside this pilot
will not accept credentials signed under it.
EOF
