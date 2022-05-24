package matcher;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleConsumer;

import matcher.classifier.nester.NestRankResult;
import matcher.classifier.nester.NestType;
import matcher.classifier.nester.NestedClassClassifier;
import matcher.config.ProjectConfig;
import matcher.type.ClassEnvironment;
import matcher.type.ClassInstance;
import matcher.type.Matchable;
import matcher.type.MethodInstance;

public class Nester {
	public Nester(ClassEnvironment env) {
		this.env = env;
	}

	public void init(ProjectConfig config, DoubleConsumer progressReceiver) {
		try {
			env.init(config, progressReceiver);
			DiscordPresence.setProject(DiscordPresence.ProjectType.NESTING);

			findPotentialNests();
		} catch (Throwable t) {
			reset();
			throw t;
		}
	}

	private void findPotentialNests() {
		for (ClassInstance clazz : env.getClassesA()) {
			clazz.equiv().getHighestPotentialScore();
		}
	}

	public void reset() {
		env.reset();
	}

	public ClassEnvironment getEnv() {
		return env;
	}

	public void addAnonymousClass(ClassInstance clazz, ClassInstance enclClass, MethodInstance enclMethod) {
		nest(clazz, (enclMethod == null) ? enclClass : enclMethod, NestType.ANONYMOUS);
		clazz.setSimpleName(null);
	}

	public void addInnerClass(ClassInstance clazz, ClassInstance enclClass) {
		addInnerClass(clazz, enclClass, clazz.getName());
	}

	public void addInnerClass(ClassInstance clazz, ClassInstance enclClass, String simpleName) {
		nest(clazz, enclClass, NestType.INNER);
		clazz.setSimpleName(simpleName);
	}

	public void nest(ClassInstance clazz, Matchable<?> nest, NestType type) {
		if (clazz == null) throw new NullPointerException("null class!");
		if (nest == null) throw new NullPointerException("null nest!");
		if (type == null) throw new NullPointerException("null nest type!");
		if (type == NestType.DUMMY) throw new NullPointerException("dummy nest type!");
		if (!clazz.isNestable()) throw new IllegalArgumentException("class " + clazz.getId() + " is not nestable!");
		if (!clazz.canNestInto(nest)) throw new IllegalArgumentException("class " + clazz.getId() + " can not be nested into " + nest.getId());

		System.out.println("nest class " + clazz + " into " + nest);

		clazz.setNest(nest, type);
	}

	public void unnest(ClassInstance clazz) {
		if (clazz == null) throw new NullPointerException("null class!");

		System.out.println("unnest class " + clazz + " from " + clazz.getNest());

		clazz.setNest(null);
		clazz.setSimpleName(null);
	}

	public void autoNestAll(DoubleConsumer progressReceiver) {
		autoNestAll(1, progressReceiver);
	}

	public void autoNestAll(int minScore, DoubleConsumer progressReceiver) {
		List<ClassInstance> classes = new ArrayList<>();

		for (ClassInstance clazz : env.getClassesA()) {
			ClassInstance equiv = clazz.equiv;

			if (equiv.isNestable() && !equiv.hasNest()) {
				classes.add(equiv);
			}
		}

		Matcher.runInParallel(classes, clazz -> {
			autoNestClass(clazz, minScore);
		}, progressReceiver);

		invalidatePotentialScores();
	}

	public void autoNestClass(ClassInstance clazz) {
		autoNestClass(clazz, 1);
	}

	public void autoNestClass(ClassInstance clazz, int minScore) {
		List<NestRankResult> results = NestedClassClassifier.rank(clazz, clazz.getEnv().getClasses(), null);

		if (results.isEmpty()) {
			return;
		}

		results.sort(null);

		int index = results.size() - 1;
		NestRankResult bestResult = results.get(index);

		while (bestResult.isDummy() && index-- > 0) {
			bestResult = results.get(index);
		}

		if (!bestResult.isDummy() && bestResult.getScore() >= minScore) {
			Matchable<?> nest = bestResult.getSubject();
			NestType type = bestResult.getType();

			nest(clazz, nest, type);

			if (type == NestType.INNER) {
				clazz.setSimpleName(clazz.getName());
			}
		}
	}

	public void invalidatePotentialScores() {
		for (ClassInstance clazz : env.getClassesA()) {
			ClassInstance equiv = clazz.equiv;

			if (equiv.isNestable() && !equiv.hasNest()) {
				equiv.markPotentialScoreDirty();
			}
		}
	}

	public NestingStatus getStatus(boolean inputsOnly) {
		int totalClassCount = 0;
		int nestedClassCount = 0;
		int anonymousClassCount = 0;
		int innerClassCount = 0;

		for (ClassInstance clazz : env.getClassesA()) {
			ClassInstance equiv = clazz.equiv;

			if (inputsOnly && !equiv.isInput()) {
				continue;
			}

			totalClassCount++;

			if (equiv.hasNest()) {
				nestedClassCount++;

				if (equiv.getSimpleName() == null) {
					anonymousClassCount++;
				} else {
					innerClassCount++;
				}
			}
		}

		return new NestingStatus(totalClassCount, nestedClassCount,
				anonymousClassCount, innerClassCount);
	}

	public static class NestingStatus {
		NestingStatus(int totalClassCount, int nestedClassCount,
				int anonymousClassCount, int innerClassCount) {
			this.totalClassCount = totalClassCount;
			this.nestedClassCount = nestedClassCount;
			this.anonymousClassCount = anonymousClassCount;
			this.innerClassCount = innerClassCount;
		}

		public final int totalClassCount;
		public final int nestedClassCount;
		public final int anonymousClassCount;
		public final int innerClassCount;
	}

	private final ClassEnvironment env;
}
