import { Stimulus } from "./stimulus";

export type RoomStatus = "waiting" | "starting" | "playing" | "finished";

// "mistake": current default -- shared turn order, wrong/timeout eliminates the
//   current turn-holder, last one alive wins.
// "hot_potato": turn only passes forward on a CORRECT answer; wrong/timeout just
//   re-prompts the same holder. The only way to be eliminated is holding the
//   turn when the hidden bomb (rooms/{roomId}/private/bomb) goes off.
// "solo_survival": reserved for the future per-player simultaneous mode -- not
//   wired into resolveRound/resolveHotPotato yet.
export type GameModeId = "mistake" | "hot_potato" | "solo_survival";

export interface RoomPlayerDoc {
  uid: string;
  displayName: string;
  avatarIndex: number;
  alive: boolean;
  order: number;
  joinedAtMs: number;
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
}

export type ResolutionReason = "correct" | "wrong" | "timeout" | "disconnect";

// Lives at rooms/{roomId}/private/bomb, blocked from all client reads by
// firestore.rules -- see the comment there. Patata Caliente's loss condition
// (whoever holds the turn when bombAtMs is reached) only works if no client
// can ever learn this value ahead of time.
export interface RoomBombDoc {
  bombAtMs: number;
}
