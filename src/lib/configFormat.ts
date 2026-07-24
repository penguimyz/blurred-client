// Friendly-form config editing (spec §5.1). The raw text editor is always the
// reliable fallback and the source of truth on save; these helpers let us layer
// a key/value form on top of the two shapes worth the effort:
//
//   - JSON  -> flat scalar keys become form fields (edits reserialize the object)
//   - properties / TOML -> `key = value` lines become fields, edited IN PLACE in
//     the raw text so comments, ordering, sections, and everything we don't
//     understand round-trip untouched.
//
// Anything that doesn't parse cleanly just falls back to raw — the form is a
// convenience, never a gate.

import type { ConfigFormat } from "../types/instance";

export interface FormEntry {
  key: string;
  value: string;
  /** Line index for line-based formats; undefined for JSON. */
  line?: number;
  /** Section header for TOML (e.g. "[general]"), for display grouping. */
  section?: string;
}

export interface ParsedConfig {
  entries: FormEntry[];
  /** True if the friendly form can safely represent this file. */
  supported: boolean;
}

// ---- JSON ----

function parseJson(text: string): ParsedConfig {
  try {
    const obj = JSON.parse(text);
    if (typeof obj !== "object" || obj === null || Array.isArray(obj)) {
      return { entries: [], supported: false };
    }
    const entries: FormEntry[] = [];
    for (const [key, value] of Object.entries(obj)) {
      if (["string", "number", "boolean"].includes(typeof value)) {
        entries.push({ key, value: String(value) });
      }
    }
    return { entries, supported: true };
  } catch {
    return { entries: [], supported: false };
  }
}

/** Rewrite one JSON scalar and reserialize, preserving the type of the field. */
export function applyJsonEdit(text: string, key: string, value: string): string {
  const obj = JSON.parse(text);
  const prev = obj[key];
  if (typeof prev === "number") {
    const n = Number(value);
    obj[key] = Number.isNaN(n) ? prev : n;
  } else if (typeof prev === "boolean") {
    obj[key] = value === "true";
  } else {
    obj[key] = value;
  }
  return JSON.stringify(obj, null, 2);
}

// ---- properties / TOML (line-based) ----

const KV_RE = /^(\s*)([A-Za-z0-9_.\-]+)(\s*=\s*)(.*?)(\s*)$/;
const SECTION_RE = /^\s*\[([^\]]+)\]\s*$/;

function parseLines(text: string): ParsedConfig {
  const lines = text.split(/\r?\n/);
  const entries: FormEntry[] = [];
  let section: string | undefined;
  lines.forEach((raw, i) => {
    const trimmed = raw.trim();
    if (trimmed === "" || trimmed.startsWith("#") || trimmed.startsWith("!") || trimmed.startsWith(";")) {
      return;
    }
    const sec = SECTION_RE.exec(raw);
    if (sec) {
      section = sec[1];
      return;
    }
    const m = KV_RE.exec(raw);
    if (m) {
      entries.push({ key: m[2], value: stripQuotes(m[4]), line: i, section });
    }
  });
  return { entries, supported: entries.length > 0 };
}

function stripQuotes(v: string): string {
  if (v.length >= 2 && ((v.startsWith('"') && v.endsWith('"')) || (v.startsWith("'") && v.endsWith("'")))) {
    return v.slice(1, -1);
  }
  return v;
}

/** Replace the value portion of a single line, keeping key, spacing, quoting. */
export function applyLineEdit(text: string, line: number, value: string): string {
  const lines = text.split(/\r?\n/);
  const raw = lines[line];
  const m = KV_RE.exec(raw);
  if (!m) return text;
  const original = m[4];
  const quoted =
    original.length >= 2 && original.startsWith('"') && original.endsWith('"')
      ? `"${value}"`
      : value;
  lines[line] = `${m[1]}${m[2]}${m[3]}${quoted}${m[5]}`;
  return lines.join("\n");
}

// ---- dispatch ----

export function parseConfig(text: string, format: ConfigFormat): ParsedConfig {
  if (format === "json") return parseJson(text);
  if (format === "properties" || format === "toml") return parseLines(text);
  return { entries: [], supported: false };
}

export function applyEdit(
  text: string,
  format: ConfigFormat,
  entry: FormEntry,
  value: string
): string {
  if (format === "json") return applyJsonEdit(text, entry.key, value);
  if (entry.line !== undefined) return applyLineEdit(text, entry.line, value);
  return text;
}
