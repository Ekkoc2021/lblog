// Token 存储混淆：
// 1. 值用 XOR + base64 编码，非明文
// 2. 真实 key 藏在无关名字下，不叫 access_token / refresh_token
// 3. 多个假 key 写入随机垃圾，外观与真值难以区分
const ACCESS_KEY = '_lb_s';
const REFRESH_KEY = '_lb_n';
const DUMMY_KEYS = ['_lb_at', '_lb_rt', '_lb_tk', '_lb_ts'];
const MAGIC = 0x5a;

// 生成与真值长度接近的随机垃圾
function randLen(min: number, max: number): number {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}
function randomChars(len: number): string {
  const cs = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=';
  let r = '';
  for (let i = 0; i < len; i++) r += cs[Math.floor(Math.random() * cs.length)];
  return r;
}

function obfuscate(v: string): string {
  let r = '';
  for (let i = 0; i < v.length; i++) r += String.fromCharCode(v.charCodeAt(i) ^ MAGIC);
  return btoa(r);
}

function reveal(v: string): string {
  try {
    const d = atob(v);
    let r = '';
    for (let i = 0; i < d.length; i++) r += String.fromCharCode(d.charCodeAt(i) ^ MAGIC);
    return r;
  } catch { return ''; }
}

// 写入假 key，值长度与真 token 接近（base64 编码后约 50-60 字符），外观无法区分
function seedDummies(): void {
  for (const k of DUMMY_KEYS) {
    localStorage.setItem(k, randomChars(randLen(48, 64)));
  }
}

export function getAccessToken(): string | null {
  const v = localStorage.getItem(ACCESS_KEY);
  return v ? reveal(v) || null : null;
}

export function getRefreshToken(): string | null {
  const v = localStorage.getItem(REFRESH_KEY);
  return v ? reveal(v) || null : null;
}

export function setTokens(access: string, refresh: string): void {
  localStorage.setItem(ACCESS_KEY, obfuscate(access));
  localStorage.setItem(REFRESH_KEY, obfuscate(refresh));
  seedDummies();
}

export function clearTokens(): void {
  localStorage.removeItem(ACCESS_KEY);
  localStorage.removeItem(REFRESH_KEY);
  for (const k of DUMMY_KEYS) localStorage.removeItem(k);
}
