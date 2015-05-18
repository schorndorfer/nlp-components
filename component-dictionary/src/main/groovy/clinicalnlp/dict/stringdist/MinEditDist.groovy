package clinicalnlp.dict.stringdist
import groovy.util.logging.Log4j
import clinicalnlp.dict.DictModelFactory

@Log4j
public class MinEditDist implements DynamicStringDist {
		
	// ------------------------------------------------------------------------
	// Inner Classes
	// ------------------------------------------------------------------------

	private static class BackPtr implements Comparable {
		Double score
		Integer startIdx
		
		@Override
		public int compareTo(Object other) {
			return Double.compare(score, ((BackPtr)other).score) 
		}
		
		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder()
			builder.append(this.score.toString())
			builder.append('(')
			builder.append(this.startIdx)
			builder.append(')')
			return builder.toString()
		}
	}
	
	// ------------------------------------------------------------------------
	// Fields
	// ------------------------------------------------------------------------
		
	CharSequence text
	StringBuilder prefix = new StringBuilder()
	Stack<BackPtr[]> rows = new Stack<>()
	Map<Integer, Integer> str2tok = new TreeMap<>()
	
	// ------------------------------------------------------------------------
	// Methods
	// ------------------------------------------------------------------------

	/**
	 * 
	 */
	@Override
	public void init(final Collection<CharSequence> tokens) {
		if (tokens == null) { throw new NullPointerException() }
		if (tokens.size() == 0) { throw new IllegalArgumentException("must have at least one token to match") }

		this.text = DictModelFactory.join(tokens, true)				
		log.info "Initialized with text: [${this.text}]"
		
		BackPtr[] bottomRow = new BackPtr[this.text.length()]
		int score = 0
		int tokIdx = -1
		for (int i = 0; i < text.size(); i++) {
			if (text[i] == DictModelFactory.TOKEN_SEP) { score = 0; tokIdx++ }
			else { this.str2tok[i] = tokIdx; }
			bottomRow[i] = new BackPtr(startIdx:i, score:score++)
		}
		rows.push(bottomRow)
	}
	
	/**
	 * 
	 */
	@Override
	public Double push(final char c) {
		prefix.append(c)
		log.info "Pushed: [${prefix}]"
		BackPtr[] toprow = rows.peek()
		BackPtr[] newrow = new BackPtr[toprow.length]
		newrow[0] = new BackPtr(score:(toprow[0].score + 1), startIdx:0)
		for (int i = 1; i < newrow.length; i++) {
			def bptrs = [
				new BackPtr(startIdx:toprow[i-1].startIdx, score:(c == text.charAt(i) ? toprow[i-1].score : toprow[i-1].score + 1 )),
				new BackPtr(startIdx:toprow[i].startIdx, score:(toprow[i].score + 1)),
				new BackPtr(startIdx:newrow[i-1].startIdx, score:(newrow[i-1].score + 1))
				]
			newrow[i] = GroovyCollections.min(bptrs)
		}
		rows.push(newrow)
		return GroovyCollections.min(newrow).score
	}

	/**
	 * 
	 */
	@Override
	public void pop() {
		if (prefix.length() == 0) { return }
		prefix.deleteCharAt(prefix.length()-1)
		this.rows.pop()
		log.info "Popped: [${prefix}]"
	}
	
	/**
	 * 
	 */
	@Override
	public Collection<Integer[]> matches(final Double tolerance) {
		Collection<Integer[]> matches = new ArrayList<>()
		BackPtr[] toprow = rows.peek()
		toprow.eachWithIndex { BackPtr bptr, Integer endIndex ->
			if (bptr.score <= tolerance && (endIndex+1 == text.size() || text.charAt(endIndex+1) == DictModelFactory.TOKEN_SEP)) {
				log.info "Match found: ${bptr.startIdx}, ${endIndex}; substring: ${text.subSequence(bptr.startIdx+1, endIndex+1)}"
				matches << ([
					(text.charAt(bptr.startIdx) == DictModelFactory.TOKEN_SEP ? this.str2tok[bptr.startIdx+1] : this.str2tok[bptr.startIdx]),
					(text.charAt(endIndex) == DictModelFactory.TOKEN_SEP ? this.str2tok[endIndex-1] : this.str2tok[endIndex]),
					] as Integer[])
			}
		}
		return matches
	}
}
