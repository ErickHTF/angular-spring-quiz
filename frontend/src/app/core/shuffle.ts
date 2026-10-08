import type { Choice } from './models';

/** Embaralhamento determinístico: cada jogador vê uma ordem fixa por pergunta. */
export function shuffleChoices(choices: Choice[], seed: string): Choice[] {
  const shuffled = choices.map((choice) => ({ ...choice }));
  let state = hashSeed(seed);

  for (let index = shuffled.length - 1; index > 0; index--) {
    state = nextRandom(state);
    const swapIndex = state % (index + 1);
    [shuffled[index], shuffled[swapIndex]] = [shuffled[swapIndex], shuffled[index]];
  }

  return shuffled.map((choice, index) => ({ ...choice, position: index + 1 }));
}

function hashSeed(seed: string): number {
  let hash = 2166136261;
  for (const character of seed) {
    hash ^= character.charCodeAt(0);
    hash = Math.imul(hash, 16777619);
  }
  return hash >>> 0;
}

function nextRandom(state: number): number {
  return (Math.imul(state, 1664525) + 1013904223) >>> 0;
}
