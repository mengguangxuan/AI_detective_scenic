export class APIError extends Error {
  constructor(readonly status: number | null, readonly code: string, message: string) { super(message); }
}
