import { Stimulus } from "./stimulus";

export type RoomStatus = "waiting" | "starting" | "playing" | "finished";

// "mistake": current default -- shared turn order, wrong/timeout eliminates the
//   current turn-holder, last one alive wins.
// "hot_potato": turn only passes forward on a CORRECT answer; wrong/timeout just
//   re-prompts the same holder. The only way to be eliminated is holding the
//   turn when the hidden bomb (rooms/{roomId}/private/bomb) goes off.
// "solo_survival": no shared turn order at all -- every player runs their own
//   independent Stroop session (own stimulus/round/score, one mistake or
//   timeout busts just that player) under one shared room-level session clock.
//   Highest score when the clock runs out wins. See soloSurvival.ts.
export type GameModeId = "mistake" | "hot_potato" | "solo_survival";

export interface RoomPlayerDoc {
  uid: string;
  displayName: string;
  avatarIndex: number;
  alive: boolean;
  order: number;
  joinedAtMs: number;
  // solo_survival only -- undefined/unused in mistake and hot_potato rooms.
  // Each player answers against their OWN stimulus/deadline instead of the
  // room's shared ones (RoomDoc.stimulus/deadlineAtMs are repurposed for this
  // mode: deadlineAtMs becomes the shared session-end clock, stimulus stays
  // null). alive here means "hasn't busted yet", same meaning as the other
  // modes, just scoped to this player's own run instead of a shared turn order.
  soloScore?: number;
  soloRound?: number;
  soloStreak?: number;
  soloStimulus?: StimulusDoc | null;
  soloDeadlineAtMs?: number | null;
  // mistake/hot_potato only -- solo_survival tracks the same idea via
  // soloScore/soloStreak instead. Accumulated live via scoring.ts's
  // applyCorrectAnswer whenever this player answers correctly.
  matchScore?: number;
  matchStreak?: number;
  // mistake/hot_potato only -- server timestamp of this player's elimination,
  // used to rank non-winners by "survived longest" once the match finishes
  // (a hot_potato match with 3-4 players can have several bomb explosions
  // before it ends, so this can accumulate more than one elimination just
  // like mistake mode).
  eliminatedAtMs?: number | null;
  // Set once, at match finish, by scoring.ts's rank*Players helpers. 1-based;
  // 1 == winner. finalScore is the profile points this player earned.
  placement?: number | null;
  finalScore?: number | null;
}

export type StimulusDoc = Stimulus;

export interface RoomDoc {
  code: string;
  status: RoomStatus;
  mode: GameModeId;
  hostUid: string;
  players: Readonly<Record<string, RoomPlayerDoc>>;
  turnOrder: readonly string[];
  turnIndex: number;
  round: number;
  stimulus: StimulusDoc | null;
  deadlineAtMs: number | null;
  winnerUid: string | null;
  createdAtMs: number;
  // Absolute server timestamp the "starting" countdown ends at. Round 1's real
  // stimulus/deadlineAtMs are only computed once beginRound fires at this
  // instant -- never at the moment startGame was called -- so every client's
  // answer window is the same full duration regardless of how long their
  // local countdown animation/render took.
  startsAtMs: number | null;
  // When the match ended, set by every finishing path. Clients measure the match duration
  // against it -- their own clock is skewed and, after a restore, far later than the finish.
  // Optional: rooms finished before it existed lack it.
  finishedAtMs?: number;
}

export type ResolutionReason = "correct" | "wrong" | "timeout" | "disconnect";

// Lives at rooms/{roomId}/private/bomb, blocked from all client reads by
// firestore.rules -- see the comment there. Patata Caliente's loss condition
// (whoever holds the turn when bombAtMs is reached) only works if no client
// can ever learn this value ahead of time.
export interface RoomBombDoc {
  bombAtMs: number;
}
