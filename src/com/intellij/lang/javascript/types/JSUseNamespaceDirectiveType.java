/*
 * Copyright 2000-2005 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.lang.javascript.types;

import java.io.IOException;

import org.jetbrains.annotations.NotNull;
import com.intellij.lang.ASTNode;
import com.intellij.lang.javascript.psi.JSStubElementType;
import com.intellij.lang.javascript.psi.JSUseNamespaceDirective;
import com.intellij.lang.javascript.psi.impl.JSUseNamespaceDirectiveImpl;
import com.intellij.lang.javascript.psi.stubs.JSUseNamespaceDirectiveStub;
import com.intellij.lang.javascript.psi.stubs.impl.JSUseNamespaceDirectiveStubImpl;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;

/**
 * @author Maxim.Mossienko
 *         Date: Oct 3, 2008
 *         Time: 9:13:01 PM
 */
public class JSUseNamespaceDirectiveType extends JSStubElementType<JSUseNamespaceDirectiveStub, JSUseNamespaceDirective>
{
	public JSUseNamespaceDirectiveType()
	{
		super("USE_NAMESPACE_DIRECTIVE");
	}

	@Override
	public JSUseNamespaceDirectiveStub newInstance(final StubInputStream dataStream, final StubElement parentStub,
			final JSStubElementType<JSUseNamespaceDirectiveStub, JSUseNamespaceDirective> type) throws IOException
	{
		return new JSUseNamespaceDirectiveStubImpl(dataStream, parentStub, type);
	}

	@Override
	public JSUseNamespaceDirectiveStub newInstance(final JSUseNamespaceDirective psi, final StubElement parentStub,
			final JSStubElementType<JSUseNamespaceDirectiveStub, JSUseNamespaceDirective> type)
	{
		return new JSUseNamespaceDirectiveStubImpl(psi, parentStub, type);
	}

	@NotNull
	@Override
	public PsiElement createElement(@NotNull ASTNode astNode)
	{
		return new JSUseNamespaceDirectiveImpl(astNode);
	}

	@Override
	public JSUseNamespaceDirective createPsi(@NotNull JSUseNamespaceDirectiveStub stub)
	{
		return new JSUseNamespaceDirectiveImpl(stub);
	}
}
