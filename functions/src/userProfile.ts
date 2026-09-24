import { getFirestore, DocumentReference } from "firebase-admin/firestore";
import { RoomDoc } from "./types";
import { roomsCol } from "./roomRepo";
import {
  achievementXpFor,
  calculateRunXp,
  isWon,
  levelFromTotalXp,
  pointsDeltaFor,
  SoloRunReport,
} from "./profileScoring";

export const USERS_COLLECTION = "users";

const MS_PER_DAY = 24 * 60 * 60 * 1000;

/**
 * How many solo runIds a profile remembers to turn a retried submission into a no-op.
 * The client retries a run until the server answers, so a duplicate only arrives while
 * that run is still at the front of its queue -- far fewer than this.
 */
export const RECENT_RUN_IDS_KEPT = 50;

/** The scoring half of users/{uid}: written only here, denied to clients by firestore.rules. */
interface ScoringFields {
  points: number;
  highScore: number;
  experience: number;
  level: number;
  matchesPlayed: number;
  matchesWon: number;
  matchesLost: number;
  dailyStreak: number;
  lastPlayedAtEpochMs: number;
  /** Achievement id -> when its XP was granted. Each one pays out once, ever. */
  awardedAchievements: Record<string, number>;
  /** The last RECENT_RUN_IDS_KEPT solo runIds applied, oldest first. */
  recentRunIds: string[];
}

export interface AppliedScore {
  points: number;
  highScore: number;
  experience: number;
  level: number;
  matchesPlayed: number;
  matchesWon: number;
  matchesLost: number;
  dailyStreak: number;
  xpAwarded: number;
}

function userRef(uid: string): DocumentReference {
  return getFirestore().collection(USERS_COLLECTION).doc(uid);
}

function readScoring(data: Record<string, unknown> | undefined): ScoringFields {
  return {
    points: (data?.points as number) ?? 0,
    highScore: (data?.highScore as number) ?? 0,
    experience: (data?.experience as number) ?? 0,
    level: (data?.level as number) ?? 1,
    matchesPlayed: (data?.matchesPlayed as number) ?? 0,
    matchesWon: (data?.matchesWon as number) ?? 0,
    matchesLost: (data?.matchesLost as number) ?? 0,
    dailyStreak: (data?.dailyStreak as number) ?? 0,
    lastPlayedAtEpochMs: (data?.lastPlayedAtEpochMs as number) ?? 0,
    awardedAchievements: (data?.awardedAchievements as Record<string, number>) ?? {},
    recentRunIds: (data?.recentRunIds as string[]) ?? [],
  };
}

function scoringOf(current: ScoringFields): Omit<AppliedScore, "xpAwarded"> {
  return {
    points: current.points,
    highScore: current.highScore,
    experience: current.experience,
    level: current.level,
    matchesPlayed: current.matchesPlayed,
    matchesWon: current.matchesWon,
    matchesLost: current.matchesLost,
    dailyStreak: current.dailyStreak,
  };
}

/** Mirrors FirebaseGameRepository.recordGameResult's day-boundary streak rule. */
export function nextDailyStreak(currentStreak: number, lastPlayedAtEpochMs: number, nowMs: number): number {
  const lastDay = Math.floor(lastPlayedAtEpochMs / MS_PER_DAY);
  const today = Math.floor(nowMs / MS_PER_DAY);
  if (today === lastDay) return currentStreak;
  if (today === lastDay + 1) return currentStreak + 1;
  return 1;
}

/**
 * Applies one validated solo run to the player's profile. The caller
 * (submitSoloRun) is responsible for having rejected implausible runs first.
 *
 * `claimedAchievementIds` are the achievements the client unlocked locally. Their
 * unlock conditions are evaluated on the device, so the server can't confirm them;
 * what it does enforce is that each id is a real achievement and pays its XP at
 * most once per account, which bounds the damage a forged claim can do.
 *
 * `runId` is the client's id for this run. The client keeps a finished run queued until
 * the server answers, so the same run can arrive twice when an answer is lost; a runId
 * seen before is a no-op. Null (older clients) skips the check.
 */
export async function applySoloRun(
  uid: string,
  run: SoloRunReport,
  claimedAchievementIds: readonly string[],
  winStreak: number = 0,
  nowMs: number = Date.now(),
  runId: string | null = null
): Promise<AppliedScore> {
  const ref = userRef(uid);

  return getFirestore().runTransaction<AppliedScore>(async (tx) => {
    const doc = await tx.get(ref);
    const current = readScoring(doc.data());
    if (runId !== null && current.recentRunIds.includes(runId)) {
      // Already applied: a retry after the first answer was lost. Report the profile as it
      // stands and pay nothing.
      return { ...scoringOf(current), xpAwarded: 0 };
    }

    const won = isWon(run);
    const isNewHighScore = run.finalScore > current.highScore;
    const dailyStreak = nextDailyStreak(current.dailyStreak, current.lastPlayedAtEpochMs, nowMs);

    const newAchievementIds = claimedAchievementIds.filter((id) => !(id in current.awardedAchievements));
    const xpAwarded = calculateRunXp(run, dailyStreak, winStreak, isNewHighScore) + achievementXpFor(newAchievementIds);

    const experience = current.experience + xpAwarded;
    const awardedAchievements = { ...current.awardedAchievements };
    for (const id of newAchievementIds) awardedAchievements[id] = nowMs;

    const applied: AppliedScore = {
      points: Math.max(current.points + pointsDeltaFor(won), 0),
      highScore: Math.max(current.highScore, run.finalScore),
      experience,
      level: levelFromTotalXp(experience),
      matchesPlayed: current.matchesPlayed + 1,
      matchesWon: won ? current.matchesWon + 1 : current.matchesWon,
      matchesLost: won ? current.matchesLost : current.matchesLost + 1,
      dailyStreak,
      xpAwarded,
    };

    tx.set(
      ref,
      {
        points: applied.points,
        highScore: applied.highScore,
        experience: applied.experience,
        level: applied.level,
        matchesPlayed: applied.matchesPlayed,
        matchesWon: applied.matchesWon,
        matchesLost: applied.matchesLost,
        dailyStreak: applied.dailyStreak,
        lastPlayedAtEpochMs: nowMs,
        awardedAchievements,
        ...(runId === null ? {} : { recentRunIds: [...current.recentRunIds, runId].slice(-RECENT_RUN_IDS_KEPT) }),
      },
      { merge: true }
    );

    return applied;
  });
}

/**
 * Pays out a finished match's server-computed finalScore to every player's profile.
 *
 * Unlike a solo run this is fully verified: the backend generated the stimuli,
 * checked each answer against its deadline and ranked the players itself
 * (scoring.ts). Idempotent via awardsAppliedAtMs on the room, so a retried
 * trigger delivery can't pay twice.
 *
 * @returns how many players were awarded (0 if the match was already settled).
 */
export async function applyMatchAwards(roomId: string, nowMs: number = Date.now()): Promise<number> {
  const roomRef = roomsCol().doc(roomId);

  return getFirestore().runTransaction<number>(async (tx) => {
    const roomDoc = await tx.get(roomRef);
    if (!roomDoc.exists) return 0;
    const room = roomDoc.data() as RoomDoc & { awardsAppliedAtMs?: number };
    if (room.status !== "finished") return 0;
    if (room.awardsAppliedAtMs != null) return 0; // already settled

    // Every ranked player played the match, including one who scored 0 (eliminated before
    // a single correct answer, or a winner whose opponents dropped out): their match
    // counters change even when their points don't.
    const players = Object.values(room.players).filter((p) => p.placement != null);
    // Every read has to happen before the first write in a Firestore transaction.
    const profiles = await Promise.all(players.map((p) => tx.get(userRef(p.uid))));

    players.forEach((player, i) => {
      const current = readScoring(profiles[i].data());
      const earned = player.finalScore ?? 0;
      const won = room.winnerUid === player.uid;
      const experience = current.experience + earned;

      tx.set(
        userRef(player.uid),
        {
          points: Math.max(current.points + earned, 0),
          experience,
          level: levelFromTotalXp(experience),
          matchesPlayed: current.matchesPlayed + 1,
          matchesWon: won ? current.matchesWon + 1 : current.matchesWon,
          matchesLost: won ? current.matchesLost : current.matchesLost + 1,
          lastPlayedAtEpochMs: nowMs,
        },
        { merge: true }
      );
    });

    tx.update(roomRef, { awardsAppliedAtMs: nowMs });
    return players.length;
  });
}
