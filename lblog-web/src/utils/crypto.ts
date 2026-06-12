/**
 * AES-256-GCM 加解密工具，用于密码本。
 * 密钥通过 PBKDF2 从用户密文派生，salt 随机生成。
 * 密文格式：base64(salt):base64(iv):base64(ciphertext)
 *
 * 优先使用原生 crypto.subtle（HTTPS/localhost），
 * HTTP 下自动降级到 @noble/ciphers + @noble/hashes 纯 JS 实现。
 */

import { pbkdf2 } from '@noble/hashes/pbkdf2.js';
import { sha256 } from '@noble/hashes/sha2.js';
import { gcm } from '@noble/ciphers/aes.js';

const KEY_LENGTH = 256;
const SALT_LENGTH = 16;
const IV_LENGTH = 12;
const PBKDF2_ITERATIONS = 200_000;

const hasSubtle = !!(typeof crypto !== 'undefined' && crypto?.subtle);

function base64ToBytes(b64: string): Uint8Array<ArrayBuffer> {
  const bin = atob(b64);
  const bytes = new Uint8Array(new ArrayBuffer(bin.length));
  for (let i = 0; i < bin.length; i++) {
    bytes[i] = bin.charCodeAt(i);
  }
  return bytes;
}

function bytesToBase64(bytes: Uint8Array): string {
  let bin = '';
  for (let i = 0; i < bytes.length; i++) {
    bin += String.fromCharCode(bytes[i]);
  }
  return btoa(bin);
}

function randomBytes(len: number): Uint8Array<ArrayBuffer> {
  return crypto.getRandomValues(new Uint8Array(new ArrayBuffer(len)));
}

// --- Native (crypto.subtle) ---

async function nativeEncrypt(plaintext: string, secret: string): Promise<string> {
  const salt = randomBytes(SALT_LENGTH);
  const iv = randomBytes(IV_LENGTH);
  const enc = new TextEncoder();
  const keyMaterial = await crypto.subtle!.importKey('raw', enc.encode(secret), 'PBKDF2', false, ['deriveKey']);
  const key = await crypto.subtle!.deriveKey(
    { name: 'PBKDF2', salt, iterations: PBKDF2_ITERATIONS, hash: 'SHA-256' },
    keyMaterial,
    { name: 'AES-GCM', length: KEY_LENGTH },
    false,
    ['encrypt'],
  );
  const ciphertext = await crypto.subtle!.encrypt({ name: 'AES-GCM', iv }, key, enc.encode(plaintext));
  return `${bytesToBase64(salt)}:${bytesToBase64(iv)}:${bytesToBase64(new Uint8Array(ciphertext))}`;
}

async function nativeDecrypt(ciphertext: string, secret: string): Promise<string> {
  const parts = ciphertext.split(':');
  if (parts.length !== 3) throw new Error('无效的密文格式');
  const salt = base64ToBytes(parts[0]);
  const iv = base64ToBytes(parts[1]);
  const data = base64ToBytes(parts[2]);
  const enc = new TextEncoder();
  const dec = new TextDecoder();
  const keyMaterial = await crypto.subtle!.importKey('raw', enc.encode(secret), 'PBKDF2', false, ['deriveKey']);
  const key = await crypto.subtle!.deriveKey(
    { name: 'PBKDF2', salt, iterations: PBKDF2_ITERATIONS, hash: 'SHA-256' },
    keyMaterial,
    { name: 'AES-GCM', length: KEY_LENGTH },
    false,
    ['decrypt'],
  );
  const plaintext = await crypto.subtle!.decrypt({ name: 'AES-GCM', iv }, key, data);
  return dec.decode(plaintext);
}

// --- Noble fallback (pure JS, works on HTTP) ---

function nobleEncrypt(plaintext: string, secret: string): string {
  const salt = randomBytes(SALT_LENGTH);
  const iv = randomBytes(IV_LENGTH);
  const enc = new TextEncoder();
  const key = pbkdf2(sha256, enc.encode(secret), salt, { c: PBKDF2_ITERATIONS, dkLen: 32 });
  const cipher = gcm(key, iv);
  const ciphertext = cipher.encrypt(enc.encode(plaintext));
  return `${bytesToBase64(salt)}:${bytesToBase64(iv)}:${bytesToBase64(ciphertext)}`;
}

function nobleDecrypt(ciphertext: string, secret: string): string {
  const parts = ciphertext.split(':');
  if (parts.length !== 3) throw new Error('无效的密文格式');
  const salt = base64ToBytes(parts[0]);
  const iv = base64ToBytes(parts[1]);
  const data = base64ToBytes(parts[2]);
  const enc = new TextEncoder();
  const dec = new TextDecoder();
  const key = pbkdf2(sha256, enc.encode(secret), salt, { c: PBKDF2_ITERATIONS, dkLen: 32 });
  const cipher = gcm(key, iv);
  const plaintext = cipher.decrypt(data);
  return dec.decode(plaintext);
}

// --- Public API ---

export const encrypt = hasSubtle
  ? (plaintext: string, secret: string): Promise<string> => nativeEncrypt(plaintext, secret)
  : (plaintext: string, secret: string): Promise<string> => Promise.resolve(nobleEncrypt(plaintext, secret));

export const decrypt = hasSubtle
  ? (ciphertext: string, secret: string): Promise<string> => nativeDecrypt(ciphertext, secret)
  : (ciphertext: string, secret: string): Promise<string> => Promise.resolve(nobleDecrypt(ciphertext, secret));
