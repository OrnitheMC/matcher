package matcher.classifier.nester;

import java.util.*;
import java.util.Map.Entry;

import matcher.type.ClassInstance;
import matcher.type.FieldInstance;
import matcher.type.Matchable;
import matcher.type.MethodInstance;

public class NestedClassClassifier {

	public static List<NestRankResult> rank(ClassInstance src, Collection<ClassInstance> dsts, NestRankResult selectedClassResult) {
		if (!src.isReal()) {
			return Collections.emptyList();
		}

		NestedClassClassifier classifier = new NestedClassClassifier(src, dsts, selectedClassResult);
		classifier.findResults();
		return classifier.finalizeResults();
	}

	private final ClassInstance clazz;
	private final ClassInstance selectedClass;

	private final Map<Matchable<?>, NestRankResult> results;

	private NestedClassClassifier(ClassInstance clazz, Collection<ClassInstance> candidateClasses, NestRankResult selectedClassResult) {
		this.results = new LinkedHashMap<>();

		this.clazz = clazz;
		this.selectedClass = (selectedClassResult == null) ? null : (ClassInstance)selectedClassResult.getSubject();
	}

	public void findResults() {
		if (clazz.hasNest()) {
			Matchable<?> nest = clazz.getNest().get();
			NestType type = clazz.getNest().getType();

			addResult(nest, type, 100);
		} else {
			tryNestClass(clazz, false);
		}
	}

	private NestRankResult bestResult(NestRankResult... results) {
		NestRankResult best = null;

		for (NestRankResult result : results) {
			if (best == null || best.compareTo(result) < 0) {
				best = result;
			}
		}

		return best;
	}

	private NestRankResult tryNestClass(ClassInstance clazz, boolean checkOnly) {
		return clazz.isNestable() ? bestResult(
				tryEnum(clazz, checkOnly),
				trySwitch(clazz, checkOnly),
				tryAnonymous(clazz, checkOnly),
				tryInner(clazz, checkOnly)) : null;
	}

	private NestRankResult tryEnum(ClassInstance clazz, boolean checkOnly) {
		if (!clazz.isEnum()) {
			return null;
		}

		ClassInstance superClass = clazz.getSuperClass();

		if (superClass.getName().equals("java/lang/Enum")) {
			return null;
		}

		return checkOrAddAnonymous(clazz, superClass, null, 100, checkOnly);
	}

	private NestRankResult trySwitch(ClassInstance clazz, boolean checkOnly) {
		// switch statements for enums are anonymous classes
		if (!clazz.canBeAnonymous()) {
			return null;
		}

		MethodInstance[] methods = clazz.getMethods();

		// switch statements only declare 1 method...
		if (methods.length != 1) {
			return null;
		}

		MethodInstance method = methods[0];

		// ... which is the class constructor
		if (!method.getName().equals("<clinit>")) {
			return null;
		}

		FieldInstance[] fields = clazz.getFields();
		FieldInstance[] syntheticFields = clazz.getSyntheticFields();

		// switch statements only declare synthetic fields...
		if (fields.length != syntheticFields.length) {
			return null;
		}

		// ...all of which are int arrays
		for (FieldInstance field : syntheticFields) {
			if (!field.getDesc().equals("[I")) {
				return null;
			}
		}

		Collection<ClassInstance> references = new LinkedHashSet<>();

		for (FieldInstance field : syntheticFields) {
			for (MethodInstance ref : field.getReadRefs()) {
				ClassInstance classRef = ref.getCls();

				if (classRef != clazz) {
					references.add(classRef.getTopLevelClass());
				}
			}
		}

		if (references.size() != 1) {
			return null;
		}

		return checkOrAddAnonymous(clazz, references.iterator().next(), null, 90, checkOnly);
	}

	private NestRankResult tryAnonymous(ClassInstance clazz, boolean checkOnly) {
		// anonymous classes cannot be interfaces, cannot be extended, cannot be
		// the type of a field or variable, or the return type of a method...
		if (!clazz.canBeAnonymous()) {
			return null;
		}

		MethodInstance[] constructors = clazz.getInstanceConstructors();

		// anonymous classes have only 1 (non-synthetic) constructor
		if (constructors.length != 1) {
			return null;
		}

		MethodInstance constr = constructors[0];
		Set<MethodInstance> references = constr.getRefsIn();

		// anonymous classes are only created once
		if (references.size() != 1) {
			return null;
		}

		MethodInstance[] methods = clazz.getDeclaredMethods();

		MethodInstance enclMethod = references.iterator().next();
		ClassInstance enclClass = enclMethod.getCls();

		if (enclClass == clazz) {
			return null;
		}

		int score = (methods.length == 1) ? 90 : (clazz.hasSyntheticFields() ? 60 : 30);

		// anonymous classes are usually package private
		if (!clazz.isPackagePrivate()) {
			score -= 5;
		}

		return checkOrAddAnonymous(clazz, enclClass, enclMethod, score, checkOnly);
	}

	private NestRankResult tryInner(ClassInstance clazz, boolean checkOnly) {
		// Inner classes usually have a synthetic field to store a
		// reference to an instance of the enclosing class. This might
		// be missing if the inner class is static.
		// There might also be synthetic methods. These are used by
		// the enclosing class to access members of the inner class.
		// The enclosing class might also have synthetic methods, used
		// by the inner class to access members of the enclosing class.
		// However, these only exist if the inner class is not static,
		// since static inner classes cannot access instance members of
		// the enclosing class.
		if (!clazz.canBeInner()) {
			return null;
		}

		// Inner classes have 1 synthetic field: a reference to
		// an instance of the enclosing class.
		if (clazz.hasSyntheticFields()) {
			FieldInstance[] fields = clazz.getSyntheticFields();

			// If there are more synthetic fields, this class is
			// probably an anonymous class...
			if (fields.length > 1) {
				return null;
			}

			FieldInstance field = fields[0];
			ClassInstance type = field.getType();

			if (type.isReal() && !type.isArray()) {
				return checkOrAddInner(clazz, type, 90, checkOnly);
			}
		}

		NestRankResult result = null;

		// If the inner class does not reference the enclosing class instance
		// at all, that synthetic field might be missing. This might be because
		// the inner class is static, or because it was removed due to being
		// unused. So then we turn to looking for synthetic methods.
		// Inner classes have static synthetic methods that enclosing classes
		// use to access private members of the inner class.
		if (clazz.hasSyntheticMethods()) {
			Collection<ClassInstance> references = new LinkedHashSet<>();
			Collection<ClassInstance> topLevelReferences = new LinkedHashSet<>();
			MethodInstance[] syntheticMethods = clazz.getSyntheticMethods();

			for (MethodInstance method : syntheticMethods) {
				for (MethodInstance ref : method.getRefsIn()) {
					ClassInstance cls = ref.getCls();

					references.add(cls);
					topLevelReferences.add(cls.getTopLevelClass());
				}
			}

			int baseScore = (topLevelReferences.size() == 1) ? 80 : 60;

			for (ClassInstance ref : references) {
				int score = baseScore;

				if (!ref.isTopLevel()) {
					score -= 20;
				}

				if (!checkOnly) {
					// Sometimes the enclosing class is mistaken for an inner class.
					// If that is more likely, reduce the score...
					NestRankResult refResult = tryNestClass(ref, true);

					if (refResult != null && refResult.getSubject() == clazz && refResult.getScore() > score) {
						score -= 20;
					}
				}

				result = bestResult(result, checkOrAddInner(clazz, ref, score, checkOnly));
			}
		}

		// If the class has no synthetic members, it might still be an inner
		// class. It might reference static synthetic methods of the enclosing
		// class
		Collection<ClassInstance> classReferences = new LinkedHashSet<>();
		Collection<ClassInstance> topLevelClassReferences = new LinkedHashSet<>();
		MethodInstance[] methods = clazz.getMethods();

		for (MethodInstance method : methods) {
			for (MethodInstance methodReference : method.getRefsOut()) {
				ClassInstance classReference = methodReference.getCls();

				if (classReference != clazz && methodReference.isStatic() && methodReference.isSynthetic()) {
					classReferences.add(classReference);
					topLevelClassReferences.add(classReference.getTopLevelClass());
				}
			}
		}

		int baseScore = (topLevelClassReferences.size() == 1) ? 60 : 40;

		for (ClassInstance ref : classReferences) {
			int score = baseScore;

			if (!ref.isTopLevel()) {
				score -= 20;
			}

			if (!checkOnly) {
				// Sometimes the enclosing class is mistaken for an inner class.
				// If that is more likely, reduce the score
				NestRankResult refResult = tryNestClass(ref, true);

				if (refResult != null && refResult.getSubject() == clazz && refResult.getScore() > score) {
					score -= 20;
				}
			}

			result = bestResult(result, checkOrAddInner(clazz, ref, score, checkOnly));
		}

		// If the inner class does not reference private members of enclosing
		// classes and enclosing classes do not reference its private members,
		// we might still find it by simply looking at where the class is
		// referenced.
		Map<ClassInstance, Integer> references = new LinkedHashMap<>();
		int total = 0;

		for (FieldInstance fieldRef : clazz.getFieldTypeRefs()) {
			ClassInstance classRef = fieldRef.getCls();

			if (!clazz.encloses(classRef)) {
				references.compute(classRef, (key, i) -> i == null ? 1 : i + 1);
				if (!classRef.isTopLevel()) {
					references.compute(classRef.getTopLevelClass(), (key, i) -> i == null ? 1 : i + 1);
				}
				total++;
			}
		}
		for (MethodInstance methodRef : clazz.getMethodTypeRefs()) {
			ClassInstance classRef = methodRef.getCls();

			if (!clazz.encloses(classRef)) {
				references.compute(classRef, (key, i) -> i == null ? 1 : i + 1);
				if (!classRef.isTopLevel()) {
					references.compute(classRef.getTopLevelClass(), (key, i) -> i == null ? 1 : i + 1);
				}
				total++;
			}
		}

		for (Entry<ClassInstance, Integer> entry : references.entrySet()) {
			ClassInstance ref = entry.getKey();
			float count = entry.getValue();

			int score = (int)(1 + (count / total) * 20);

			if (!checkOnly) {
				// Sometimes the enclosing class is mistaken for an inner class.
				// If that is more likely, reduce the score
				NestRankResult refResult = tryNestClass(ref, true);

				if (refResult != null && refResult.getSubject() == clazz && refResult.getScore() > score) {
					score -= 20;
				}
			}

			result = bestResult(result, checkOrAddInner(clazz, ref, score, checkOnly));
		}

		return result;
	}

	private NestRankResult checkOrAddAnonymous(ClassInstance clazz, ClassInstance enclClass, MethodInstance enclMethod, int score, boolean checkOnly) {
		Matchable<?> parent = (enclMethod == null) ? enclClass : enclMethod;

		if (clazz.canNestInto(parent)) {
			return checkOnly ? NestRankResult.maybe(parent, NestType.ANONYMOUS, score) : addResult(parent, NestType.ANONYMOUS, score);
		} else {
			return null;
		}
	}

	private NestRankResult checkOrAddInner(ClassInstance clazz, ClassInstance enclClass, int score, boolean checkOnly) {
		if (clazz.canNestInto(enclClass)) {
			return checkOnly ? NestRankResult.maybe(enclClass, NestType.INNER, score) : addResult(enclClass, NestType.INNER, score);
		} else {
			return null;
		}
	}

	private NestRankResult addResult(Matchable<?> nest, NestType type, int score) {
		return addResult(NestRankResult.maybe(nest, type, score));
	}

	private NestRankResult addResult(NestRankResult result) {
		Matchable<?> subject = result.getSubject();

		switch (subject.getKind()) {
		case METHOD:
			MethodInstance method = (MethodInstance)subject;

			ClassInstance clazz = method.getCls();
			int score = result.getScore();
			NestRankResult classResult = NestRankResult.maybe(clazz, NestType.DUMMY, score);

			if (selectedClass == null || clazz == selectedClass) {
				addResultNoCheck(result);
			}

			return addResultNoCheck(classResult);
		case CLASS:
			return addResultNoCheck(result);
		default:
			return null;
		}
	}

	private NestRankResult addResultNoCheck(NestRankResult result) {
		return results.compute(result.getSubject(), (key, oldResult) -> oldResult == null || result.getScore() > oldResult.getScore() ? result : oldResult);
	}

	public List<NestRankResult> finalizeResults() {
		List<NestRankResult> finalResults = new ArrayList<>(results.values());

		Set<ClassInstance> unrankedClasses = new HashSet<>(clazz.getEnv().getClasses());

		unrankedClasses.removeAll(results.keySet());
		unrankedClasses.remove(clazz);

		for (ClassInstance unrankedClass : unrankedClasses) {
			finalResults.add(NestRankResult.no(unrankedClass));
		}

		if (selectedClass != null) {

			Set<MethodInstance> unrankedMethods = new HashSet<>(Arrays.asList(selectedClass.getMethods()));

			unrankedMethods.removeAll(results.keySet());

			for (MethodInstance unrankedMethod : unrankedMethods) {
				finalResults.add(NestRankResult.no(unrankedMethod));
			}
		}

		return finalResults;
	}
}
