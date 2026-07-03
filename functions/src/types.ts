import { StroopColorId } from "./stimulus";

export type RoomStatus = "waiting" | "playing" | "finished";

export interface RoomPlayerDoc {
  uid: string;
  displayName: string;
  avatarIndex: number;
  alive: boolean;
  order: number;
  joinedAt: number;
}

export interface StimulusDoc {
  wordLabel: StroopColorId;
  inkColor: StroopColorId;
  options: StroopColorId[];
}

export interface RoomDoc {
  code: string;
  status: RoomStatus;
  hostUid: string;
  players: Record<string, RoomPlayerDoc>;
  turnOrder: string[];
  turnIndex: number;
  round: number;
  stimulus: StimulusDoc | null;
  deadlineAtMs: number | null;
  winnerUid: string | null;
  createdAtMs: number;
}

export type ResolutionReason = "correct" | "wrong" | "timeout" | "disconnect";
