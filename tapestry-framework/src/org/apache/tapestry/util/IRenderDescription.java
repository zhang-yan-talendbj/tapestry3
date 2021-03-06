//  Copyright 2004 The Apache Software Foundation
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.apache.tapestry.util;

import org.apache.tapestry.IMarkupWriter;

/**
 *  An object which may render a description of itself, which is used in debugging
 *  (i.e., by the Inspector).
 *
 *  @author Howard Lewis Ship
 *  @version $Id$
 *  @since 1.0.6
 * 
 **/

public interface IRenderDescription
{
	/**
	 *  Object should render a description of itself to the writer.
	 *
	 **/

	public void renderDescription(IMarkupWriter writer);
}