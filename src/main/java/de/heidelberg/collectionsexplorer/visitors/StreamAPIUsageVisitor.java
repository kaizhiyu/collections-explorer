package de.heidelberg.collectionsexplorer.visitors;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.pmw.tinylog.Logger;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade;
import com.github.javaparser.symbolsolver.model.resolution.TypeSolver;

import de.heidelberg.collectionsexplorer.Filter;
import de.heidelberg.collectionsexplorer.beans.StreamOperationsInfo;
import de.heidelberg.collectionsexplorer.beans.StreamOperationsInfo.StreamOperationsInfoBuilder;
import de.heidelberg.collectionsexplorer.beans.StringListInfo;
import de.heidelberg.collectionsexplorer.context.Result;
import de.heidelberg.collectionsexplorer.util.ParserUtil;

public class StreamAPIUsageVisitor extends VoidVisitorAdapter<Result<StreamOperationsInfo>> {

	private static final String UNKNOWN_TYPE = "Undescribed";
	
	private static final String STREAM = "stream";
	private static final String PARALLEL_STREAM = "parallelStream";
	
	private TypeSolver solver;

	public StreamAPIUsageVisitor(Filter filter, TypeSolver s) {
		solver = s;
	}

	@Override
	public void visit(final MethodCallExpr n, final Result<StreamOperationsInfo> result) {

		// super.visit(n,arg);
		List<MethodCallExpr> allExpCalls = n.findAll(MethodCallExpr.class);

		// Get stream operations
		StreamOperationsInfo info = extractStreamOperations(n, allExpCalls, STREAM);
		// FIXME: This class can be more generic - extract all methodCall until you
		// reach a particular methodCall
		if (info != null) {
			result.add(info);
		}

		// Get parallel stream operations
		StreamOperationsInfo parallelInfo = extractStreamOperations(n, allExpCalls, PARALLEL_STREAM);
		if (parallelInfo != null) {
			result.add(parallelInfo);
		}
	}

	private StreamOperationsInfo extractStreamOperations(MethodCallExpr n, 
			List<MethodCallExpr> allExpCalls, String streamAnchor) {

		// Find if there is a stream method call in the chain
		Optional<MethodCallExpr> streamMethodCall = allExpCalls.stream()
				.filter(x -> x.getNameAsString().equals(streamAnchor)).findAny();

		if (streamMethodCall.isPresent()) { // Stream method confirmed

			
			StreamOperationsInfoBuilder builder = StreamOperationsInfo.builder();
			
			// Class Name
			builder.className(ParserUtil.retrieveClass(n));
			
			// Position (line + col)
			builder.lineNumber(ParserUtil.getLineNumber(n));
			builder.columnNumber(ParserUtil.getColumn(n));
			
			// Stream chain operations
			StringListInfo chain = extractMethodChain(n, streamAnchor);
			builder.streamOperations(chain);

			// Source type
			Optional<Expression> scope = streamMethodCall.get().getScope();
			String type = extractType(scope);
			builder.sourceType(type);
			
			return builder.build();
		}

		return null;
	}

	private StringListInfo extractMethodChain(MethodCallExpr expr, String streamAnchor) {
		
		List<MethodCallExpr> allMethodCalls = expr.findAll(MethodCallExpr.class);
		
		List<String> streamChain = allMethodCalls.stream()
				.takeWhile(x -> !x.getNameAsString().equals(streamAnchor))
				.map(x -> x.getNameAsString())
				.collect(Collectors.toList());
		
		// We add the stream/parallelstream as our search above does not
		// cover the stream method itself (takeWhile)
		streamChain.add(streamAnchor);
		
		// We need to reverse here as we walk 
		// from the last operation -> stream
		Collections.reverse(streamChain);
		
		return new StringListInfo(streamChain);
	}

	private String extractType(Optional<Expression> scope) {
		try {
			if (scope.isPresent()) {
				ResolvedType rt = JavaParserFacade.get(solver).getType(scope.get());
				return rt.describe();
			}
		} catch (Exception e) {
			// Trace level as this is expected to happen quite often
			Logger.trace(String.format("Error while identifying the types for the scope = %s", scope.get().toString()));
		}
		return UNKNOWN_TYPE;
	}

}
