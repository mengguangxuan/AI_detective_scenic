import { describe, expect, it } from 'vitest';
import { applyPreviewAction, initialPreview, previewJournal, visiblePoints } from './previewRules';
import { previewSessionSchema } from '../objects/PreviewSession';

describe('local preview rules', () => {
  it('does not expose or read the letter before opening the drawer', () => {
    expect(visiblePoints(initialPreview)).toEqual(['point_drawer']);
    expect(applyPreviewAction(initialPreview, 'read-letter')).toBe(initialPreview);
    expect(previewJournal(initialPreview)).toEqual([]);
  });
  it('opens, reads and keeps repeated actions idempotent', () => {
    const opened = applyPreviewAction(initialPreview, 'open-drawer');
    expect(visiblePoints(opened)).toContain('point_letter');
    expect(applyPreviewAction(opened, 'open-drawer')).toBe(opened);
    const read = applyPreviewAction(opened, 'read-letter');
    expect(read.sequence).toBe(2);
    expect(read.letterRead).toBe(true);
    expect(applyPreviewAction(read, 'read-letter')).toBe(read);
    expect(previewJournal(read)).toHaveLength(2);
    expect(initialPreview.drawerOpened).toBe(false);
  });
  it('rejects corrupt, unknown-version and inconsistent saved state', () => {
    expect(previewSessionSchema.safeParse({ ...initialPreview, letterRead: true }).success).toBe(false);
    expect(previewSessionSchema.safeParse({ ...initialPreview, version: 2 }).success).toBe(false);
    expect(previewSessionSchema.safeParse({ ...initialPreview, sequence: 1 }).success).toBe(false);
    expect(previewSessionSchema.safeParse({ ...initialPreview, extra: 'unknown' }).success).toBe(false);
  });
});
