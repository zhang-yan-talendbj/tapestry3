/* ====================================================================
 * The Apache Software License, Version 1.1
 *
 * Copyright (c) 2000-2003 The Apache Software Foundation.  All rights
 * reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution,
 *    if any, must include the following acknowledgment:
 *       "This product includes software developed by the
 *        Apache Software Foundation (http://apache.org/)."
 *    Alternately, this acknowledgment may appear in the software itself,
 *    if and wherever such third-party acknowledgments normally appear.
 *
 * 4. The names "Apache" and "Apache Software Foundation", "Tapestry" 
 *    must not be used to endorse or promote products derived from this
 *    software without prior written permission. For written
 *    permission, please contact apache@apache.org.
 *
 * 5. Products derived from this software may not be called "Apache" 
 *    or "Tapestry", nor may "Apache" or "Tapestry" appear in their 
 *    name, without prior written permission of the Apache Software Foundation.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE TAPESTRY CONTRIBUTOR COMMUNITY
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.tapestry.vlib;

import java.io.IOException;
import java.rmi.RemoteException;

import javax.ejb.CreateException;
import javax.ejb.FinderException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpSession;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.tapestry.ApplicationRuntimeException;
import org.apache.tapestry.IRequestCycle;
import org.apache.tapestry.StaleSessionException;
import org.apache.tapestry.engine.BaseEngine;
import org.apache.tapestry.form.IPropertySelectionModel;
import org.apache.tapestry.request.ResponseOutputStream;
import org.apache.tapestry.vlib.ejb.IBookQuery;
import org.apache.tapestry.vlib.ejb.IBookQueryHome;
import org.apache.tapestry.vlib.ejb.IOperations;
import org.apache.tapestry.vlib.ejb.IOperationsHome;
import org.apache.tapestry.vlib.ejb.Person;
import org.apache.tapestry.vlib.ejb.Publisher;
import org.apache.tapestry.vlib.pages.ApplicationUnavailable;

/**
 *
 *  The engine for the Virtual Library.  
 *  This exists to implement the external 
 *  service, which allows the {@link org.apache.tapestry.vlib.pages.ViewBook}
 *  and {@link org.apache.tapestry.vlib.pages.PersonPage}
 *  pages to be bookmarked, and to provide
 *  a way for shutting down the application when the user logs out.
 *
 *
 *  @version $Id$
 *  @author Howard Lewis Ship
 *
 **/

public class VirtualLibraryEngine extends BaseEngine
{
    public static final Log LOG = LogFactory.getLog(VirtualLibraryEngine.class);

    private static final boolean DEBUG_ENABLED =
        Boolean.getBoolean("org.apache.tapestry.vlib.debug-enabled");

    private transient boolean _killSession;

    private transient IOperations _operations;
    private transient IPropertySelectionModel _publisherModel;
    private transient IPropertySelectionModel _personModel;

    private transient String _applicationUnavailableMessage;

    /**
     *  Removes the operations bean instance, if accessed this request cycle.
     *
     *  <p>May invalidate the {@link HttpSession} (see {@link #logout()}).
     **/

    protected void cleanupAfterRequest(IRequestCycle cycle)
    {
        clearCache();

        _applicationUnavailableMessage = null;

        if (_killSession)
        {
            try
            {
                HttpSession session = cycle.getRequestContext().getSession();

                if (session != null)
                    session.invalidate();
            }
            catch (IllegalStateException ex)
            {
                // Ignore.
            }
        }
    }

    /**
     *  Sets the visit property to null, and sets a flag that
     *  invalidates the {@link HttpSession} at the end of the request cycle.
     *
     **/

    public void logout()
    {
        Visit visit = (Visit) getVisit();

        if (visit != null)
            visit.setUser(null);

        _killSession = true;
    }

    public boolean isDebugEnabled()
    {
        return DEBUG_ENABLED;
    }

    /**
     *  Returns an instance of the Vlib Operations beans, which is a stateless
     *  session bean for performing certain operations.
     *
     *  <p>The bean is automatically removed at the end of the request cycle.
     *
     **/

    public IOperations getOperations()
    {
        Global global = (Global) getGlobal();

        if (_operations == null)
        {
            int i = 0;
            while (true)
            {
                try
                {
                    IOperationsHome home = global.getOperationsHome();

                    _operations = home.create();

                    break;
                }
                catch (CreateException ex)
                {
                    throw new ApplicationRuntimeException("Error creating operations bean.", ex);
                }
                catch (RemoteException ex)
                {
                    rmiFailure("Remote exception creating operations bean.", ex, i++);
                }
            }
        }

        return _operations;
    }

    /**
     *  Builds a model for entering in a publisher name, including an intial
     *  blank option.
     *
     **/

    public IPropertySelectionModel getPublisherModel()
    {
        if (_publisherModel == null)
            _publisherModel = buildPublisherModel();

        return _publisherModel;
    }

    private IPropertySelectionModel buildPublisherModel()
    {
        Publisher[] publishers = null;

        EntitySelectionModel model = new EntitySelectionModel();

        // Add in a default null value, such that the user can
        // not select a specific Publisher.

        model.add(null, "");

        int i = 0;
        while (true)
        {
            IOperations operations = getOperations();

            try
            {
                publishers = operations.getPublishers();

                // Exit the retry loop

                break;
            }
            catch (RemoteException ex)
            {
                rmiFailure("Unable to obtain list of publishers.", ex, i++);
            }
        }

        // Add in the actual publishers.  They are sorted by name.

        for (i = 0; i < publishers.length; i++)
            model.add(publishers[i].getId(), publishers[i].getName());

        return model;
    }

    /**
     *  Invoked from {@link Visit#clearCache()} (and at the end of the request
     *  cycle) to clear the publisher and person
     *  {@link IPropertySelectionModel} models.
     *
     **/

    public void clearCache()
    {
        _publisherModel = null;
        _personModel = null;

        Visit visit = (Visit) getVisit();

        if (visit != null)
            visit.clearCache();
    }

    /**
     *  Returns a model that contains all the known Person's, sorted by last name,
     *  then first.  The label for the model matches the user's natural name.
     *
     **/

    public IPropertySelectionModel getPersonModel()
    {
        if (_personModel == null)
            _personModel = buildPersonModel(false);

        return _personModel;
    }

    public IPropertySelectionModel buildPersonModel(boolean includeEmpty)
    {
        Person[] persons = null;

        int i = 0;
        while (true)
        {
            IOperations operations = getOperations();

            try
            {
                persons = operations.getPersons();

                break;
            }
            catch (RemoteException ex)
            {
                rmiFailure("Unable to obtain list of persons.", ex, i++);
            }
        }

        EntitySelectionModel model = new EntitySelectionModel();

        if (includeEmpty)
            model.add(null, "");

        for (i = 0; i < persons.length; i++)
            model.add(persons[i].getId(), persons[i].getNaturalName());

        return model;

    }

    /**
     *  Creates a new {@link IBookQuery} EJB instance.
     *
     **/

    public IBookQuery createNewQuery()
    {
        Global global = (Global) getGlobal();

        IBookQuery result = null;

        int i = 0;
        while (true)
        {
            IBookQueryHome home = global.getBookQueryHome();

            try
            {
                result = home.create();

                break;
            }
            catch (CreateException ex)
            {
                throw new ApplicationRuntimeException("Could not create BookQuery bean.", ex);
            }
            catch (RemoteException ex)
            {
                rmiFailure("Remote exception creating BookQuery bean.", ex, i++);
            }
        }

        return result;
    }

    /**
     *  Invoked in various places to present an error message to the user.
     *  This sets the error property of either the 
     *  {@link org.apache.tapestry.vlib.pages.Home} or
     *  {@link org.apache.tapestry.vlib.pages.MyLibrary} page 
     *  (the latter only if the user is logged in),
     *  and sets the selected page for rendering the response.
     *
     **/

    public void presentError(String error, IRequestCycle cycle)
    {
        String pageName = "Home";
        // Get, but don't create, the visit.
        Visit visit = (Visit) getVisit();

        if (visit != null && visit.isUserLoggedIn())
            pageName = "MyLibrary";

        IErrorProperty page = (IErrorProperty) cycle.getPage(pageName);

        page.setError(error);

        cycle.setPage(page);
    }

    /**
     *  Invoked after an operation on a home or remote interface
     *  throws a RemoteException; this clears any cache of
     *  home and remote interfaces.  
     *
     * @param message the message for the exception, or for the log message
     * @param ex the exception thrown
     * @param attempt the attempt number.  Attempt #0 simply clears the EJBs,
     *  attempt #1 is the real failure.
     *
     **/

    public void rmiFailure(String message, RemoteException ex, int attempt)
    {
        LOG.error(message, ex);

        clearEJBs();

        if (attempt > 0)
            punt(message, ex);

    }

    private void punt(String message, Throwable ex)
    {
        _applicationUnavailableMessage = message;

        throw new ApplicationRuntimeException(message, ex);
    }

    private void clearEJBs()
    {
        Global global = (Global) getGlobal();

        global.clear();

        _operations = null;
    }

    /**
     *  Invoked when any kind of runtime exception percolates up to the
     *  top level service method.  Normally, the standard Exception
     *  page is displayed; we logout and setup our own version of the page
     *  instead.
     * 
     **/

    protected void activateExceptionPage(
        IRequestCycle cycle,
        ResponseOutputStream output,
        Throwable cause)
        throws ServletException
    {
        try
        {
            logout();

            ApplicationUnavailable page =
                (ApplicationUnavailable) cycle.getPage("ApplicationUnavailable");

            String message = _applicationUnavailableMessage;

            if (message == null)
                message = cause.getMessage();

            if (message == null)
                message = cause.getClass().getName();

            page.activate(message, cause);

            cycle.setPage(page);

            renderResponse(cycle, output);
        }
        catch (Throwable t)
        {
            super.activateExceptionPage(cycle, output, cause);
        }
    }

    /**
     *  Reads a person by id.
     * 
     **/

    public Person readPerson(Integer personId)
    {
        Person result = null;

        int i = 0;
        while (true)
        {
            IOperations operations = getOperations();

            try
            {
                result = operations.getPerson(personId);

                break;
            }
            catch (FinderException ex)
            {
                throw new ApplicationRuntimeException("No such Person #" + personId + ".", ex);
            }
            catch (RemoteException ex)
            {
                rmiFailure("Unable to read Person #" + personId + ".", ex, i++);
            }
        }

        return result;
    }

    protected void handleStaleSessionException(
        StaleSessionException ex,
        IRequestCycle cycle,
        ResponseOutputStream output)
        throws IOException, ServletException
    {
		IMessageProperty home = (IMessageProperty)cycle.getPage("Home");
		
		home.setMessage("You have been logged out due to inactivity.");
		
		redirect("Home", cycle, output, ex);
    }

}