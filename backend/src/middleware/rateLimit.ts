import { Request, Response, NextFunction } from 'express';

type Bucket = { count: number; resetAt: number };

/**
 * Fixed-window in-memory rate limiter. Works for a single-instance deployment
 * (which is what Render's smallest tier gives you). For multi-instance,
 * swap the Map for a Redis INCR+EXPIRE — the handler shape stays the same.
 */
export function rateLimit(opts: {
  windowMs: number;
  max: number;
  keyBy: (req: Request) => string;
  message?: string;
}) {
  const buckets = new Map<string, Bucket>();
  const { windowMs, max, keyBy, message = 'Too many requests. Please try again later.' } = opts;

  // Lightweight sweep so the Map doesn't grow unbounded.
  setInterval(() => {
    const now = Date.now();
    for (const [k, b] of buckets) if (b.resetAt <= now) buckets.delete(k);
  }, Math.max(windowMs, 60_000)).unref();

  return (req: Request, res: Response, next: NextFunction) => {
    const key = keyBy(req);
    if (!key) return next(); // nothing to key on — skip rather than block
    const now = Date.now();
    const b = buckets.get(key);
    if (!b || b.resetAt <= now) {
      buckets.set(key, { count: 1, resetAt: now + windowMs });
      return next();
    }
    if (b.count >= max) {
      const retrySec = Math.max(1, Math.ceil((b.resetAt - now) / 1000));
      res.setHeader('Retry-After', String(retrySec));
      return res.status(429).json({ error: 'RATE_LIMITED', message, retryAfterSec: retrySec });
    }
    b.count += 1;
    next();
  };
}

/** Prefers the first X-Forwarded-For hop (Render / Nginx) then falls back. */
export function clientIp(req: Request): string {
  const fwd = req.header('x-forwarded-for');
  if (fwd) return fwd.split(',')[0].trim();
  return req.ip || req.socket.remoteAddress || 'unknown';
}
