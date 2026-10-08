export function totalVotes(counts?: Record<string, number>): number {
  return Object.values(counts ?? {}).reduce((sum, count) => sum + count, 0);
}

export function voteShare(counts: Record<string, number> | undefined, choiceId: string): number {
  const total = totalVotes(counts);
  if (total === 0) return 0;
  return Math.round(((counts?.[choiceId] ?? 0) / total) * 100);
}
