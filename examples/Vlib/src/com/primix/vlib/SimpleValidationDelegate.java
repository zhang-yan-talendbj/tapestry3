/*
 * Tapestry Web Application Framework
 * Copyright (c) 2000-2001 by Howard Lewis Ship
 *
 * Howard Lewis Ship
 * http://sf.net/projects/tapestry
 * mailto:hship@users.sf.net
 *
 * This library is free software.
 *
 * You may redistribute it and/or modify it under the terms of the GNU
 * Lesser General Public License as published by the Free Software Foundation.
 *
 * Version 2.1 of the license should be included with this distribution in
 * the file LICENSE, as well as License.html. If the license is not
 * included with this distribution, you may find a copy at the FSF web
 * site at 'www.gnu.org' or 'www.fsf.org', or you may write to the
 * Free Software Foundation, 675 Mass Ave, Cambridge, MA 02139 USA.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied waranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 */

package com.primix.vlib;

import com.primix.tapestry.*;
import com.primix.tapestry.valid.*;

/**
 *  Implementation of {@link IValidationDelegate} that
 *  works with pages that implements {@link IErrorProperty}.
 *
 * @author Howard Ship
 * @version $Id$
 */

public class SimpleValidationDelegate extends ValidationDelegate
{
	public void writeLabelPrefix(
		IField field,
		IResponseWriter writer,
		IRequestCycle cycle)
	{
		if (isInError(field))
		{
			writer.begin("span");
			writer.attribute("class", "clsInvalidField");
		}
	}

	public void writeLabelSuffix(
		IField field,
		IResponseWriter writer,
		IRequestCycle cycle)
	{
		if (isInError(field))
			writer.end();
	}

	public void writeSuffix(
		IResponseWriter writer,
		IRequestCycle cycle)
	{
		if (currentTracking != null)
		{
		writer.print(" ");
		writer.begin("span");
		writer.attribute("class", "clsInvalidField");
		writer.print("**");
		writer.end();
		}
	}
}