import { Stimulus } from "./stimulus";

export type RoomStatus = "waiting" | "playing" | "finished";

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
  hostUid: string;
  players: Readonly<Record<string, RoomPlayerDoc>>;
  turnOrder: readonly string[];
  turnIndex: number;
  round: number;
  stimulus: StimulusDoc | null;
  deadlineAtMs: number | null;
  winnerUid: string | null;
  createdAtMs: number;
}

export type ResolutionReason = "correct" | "wrong" | "timeout" | "disconnect";
