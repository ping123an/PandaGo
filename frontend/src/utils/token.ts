/** Token 本地存储工具。阶段 3 接入 JWT 后由登录流程调用。 */
const TOKEN_KEY = 'pandago_token'

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token)
}

export function removeToken(): void {
  localStorage.removeItem(TOKEN_KEY)
}
