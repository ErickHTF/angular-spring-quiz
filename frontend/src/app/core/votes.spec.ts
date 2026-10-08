import { totalVotes, voteShare } from './votes';

describe('votes', () => {
  it('sums all counts and treats missing counts as zero', () => {
    expect(totalVotes({ a: 2, b: 3 })).toBe(5);
    expect(totalVotes(undefined)).toBe(0);
  });

  it('computes rounded percentage per choice', () => {
    expect(voteShare({ a: 1, b: 2 }, 'a')).toBe(33);
    expect(voteShare({ a: 1, b: 2 }, 'c')).toBe(0);
    expect(voteShare({}, 'a')).toBe(0);
  });
});
