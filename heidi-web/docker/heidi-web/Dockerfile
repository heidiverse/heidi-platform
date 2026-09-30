# SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
# SPDX-License-Identifier: Apache-2.0

FROM nginx as base

FROM base as builder

COPY conf/nginx.conf /etc/nginx/nginx.conf
COPY conf/default.conf /etc/nginx/conf.d/default.conf

WORKDIR /app
ADD bin/heidi-web.tar.gz .

FROM base as runner

COPY --from=builder /etc/nginx/nginx.conf /etc/nginx/nginx.conf
COPY --from=builder /etc/nginx/conf.d/default.conf /etc/nginx/conf.d/default.conf

WORKDIR /var/www/web
COPY --from=builder /app/dist .
