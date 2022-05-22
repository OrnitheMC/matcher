package matcher.classifier.nester;

import java.util.Objects;

import matcher.type.ClassInstance;
import matcher.type.Matchable;
import matcher.type.MemberInstance;
import matcher.type.MethodInstance;

public class Nest {
	private final Matchable<?> subject;
	private final NestType type;

	public Nest(Matchable<?> subject, NestType type) {
		this.subject = subject;
		this.type = type;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof Nest nest)) {
			return false;
		}

		return type == nest.type && subject == nest.subject;
	}

	@Override
	public int hashCode() {
		return Objects.hash(subject, type);
	}

	@Override
	public String toString() {
		return subject.toString();
	}

	public Matchable<?> get() {
		return subject;
	}

	public ClassInstance getEnclosingClass() {
		if (subject instanceof ClassInstance) {
			return (ClassInstance)subject;
		}
		if (subject instanceof MemberInstance) {
			return ((MemberInstance<?>)subject).getCls();
		}

		return null; // we should never get here
	}

	public MethodInstance getEnclosingMethod() {
		if (subject instanceof MemberInstance) {
			return (MethodInstance)subject;
		}

		return null;
	}

	public NestType getType() {
		return type;
	}
}
