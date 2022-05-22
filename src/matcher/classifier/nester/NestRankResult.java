package matcher.classifier.nester;

import matcher.classifier.IRankResult;
import matcher.type.Matchable;

public class NestRankResult implements IRankResult, Comparable<NestRankResult> {
	private final Matchable<?> subject;
	private final NestType type;
	private final int score;

	public static NestRankResult yes(Matchable<?> subject, NestType type) {
		return maybe(subject, type, 100);
	}

	public static NestRankResult no(Matchable<?> subject) {
		return maybe(subject, NestType.DUMMY, 0);
	}

	public static NestRankResult maybe(Matchable<?> subject, NestType type, int score) {
		if (score < 0) {
			score = 0;
		} else
		if (score > 100) {
			score = 100;
		}

		return new NestRankResult(subject, type, score);
	}

	private NestRankResult(Matchable<?> subject, NestType type, int score) {
		this.subject = subject;
		this.type = type;
		this.score = score;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj instanceof NestRankResult result) {
			return subject == result.subject;
		}

		return false;
	}

	@Override
	public int hashCode() {
		return subject.hashCode();
	}

	@Override
	public int compareTo(NestRankResult result) {
		if (result == null) {
			return 1;
		}

		if (score < result.score) {
			return -1;
		}
		if (score > result.score) {
			return 1;
		}

		boolean dummy = isDummy();
		boolean resultDummy = result.isDummy();

		if (dummy && !resultDummy) {
			return -1;
		}
		if (!dummy && resultDummy) {
			return 1;
		}

		return 0;
	}

	public Matchable<?> getSubject() {
		return subject;
	}

	public NestType getType() {
		return type;
	}

	public boolean isDummy() {
		return type == NestType.DUMMY;
	}

	public int getScore() {
		return score;
	}
}