// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { UserRole, type UserTokenData } from "@/lib/auth/identity";

export function isEditor(user: UserTokenData) {
  return [UserRole.SuperAdmin, UserRole.Admin, UserRole.Editor].some((role) =>
    user?.roles.includes(role),
  );
}

export function isOperator(user: UserTokenData) {
  return [UserRole.SuperAdmin, UserRole.Admin, UserRole.Operator].some((role) =>
    user?.roles.includes(role),
  );
}

export function isManager(user: UserTokenData) {
  return [UserRole.SuperAdmin, UserRole.Admin, UserRole.Manager].some((role) =>
    user?.roles.includes(role),
  );
}

export function isDeveloper(user: UserTokenData) {
  return [UserRole.SuperAdmin, UserRole.Admin, UserRole.Developer].some(
    (role) => user?.roles.includes(role),
  );
}
