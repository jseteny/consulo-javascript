package com.sixrr.inspectjs.functionmetrics;

import com.intellij.lang.javascript.psi.JSBlockStatement;
import com.intellij.lang.javascript.psi.JSFunction;
import com.intellij.psi.PsiElement;
import com.sixrr.inspectjs.BaseInspectionVisitor;
import com.sixrr.inspectjs.InspectionJSBundle;
import com.sixrr.inspectjs.JSGroupNames;
import org.jetbrains.annotations.NotNull;

public class StatementsPerFunctionJSInspection
        extends FunctionMetricsInspection {
    @Override
	@NotNull
    public String getID() {
        return "FunctionTooLongJS";
    }

    @Override
	@NotNull
    public String getDisplayName() {
        return InspectionJSBundle.message("overly.long.function.display.name");
    }

    @Override
	@NotNull
    public String getGroupDisplayName() {
        return JSGroupNames.FUNCTIONMETRICS_GROUP_NAME;
    }

    @Override
	protected int getDefaultLimit() {
        return 30;
    }

    @Override
	protected String getConfigurationLabel() {
        return InspectionJSBundle.message("maximum.statements.per.function");
    }

    @Override
	public String buildErrorString(Object... args) {
        final JSFunction function = (JSFunction) ((PsiElement) args[0]).getParent();
        assert function != null;
        final PsiElement lastChild = function.getLastChild();
        final StatementCountVisitor visitor = new StatementCountVisitor();
        assert lastChild != null;
        lastChild.accept(visitor);
        final int coupling = visitor.getStatementCount();
        if (functionHasIdentifier(function)) {
            return InspectionJSBundle.message("function.is.overly.long.statement.error.string", coupling);
        } else {
            return InspectionJSBundle.message("anonymous.function.is.overly.long.statement.error.string", coupling);
        }
    }

    @Override
	public BaseInspectionVisitor buildVisitor() {
        return new Visitor();
    }

    private class Visitor extends BaseInspectionVisitor {

        @Override public void visitJSFunctionDeclaration(@NotNull JSFunction function) {

            final PsiElement lastChild = function.getLastChild();
            if(!(lastChild instanceof JSBlockStatement))
            {
                return;
            }
            final StatementCountVisitor visitor = new StatementCountVisitor();
            lastChild.accept(visitor);
            final int statementCount = visitor.getStatementCount();

            if (statementCount <= getLimit()) {
                return;
            }
            registerFunctionError(function);
        }
    }
}

