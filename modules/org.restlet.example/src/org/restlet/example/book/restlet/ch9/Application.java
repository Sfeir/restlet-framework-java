/*
 * Copyright 2005-2008 Noelios Consulting.
 * 
 * The contents of this file are subject to the terms of the Common Development
 * and Distribution License (the "License"). You may not use this file except in
 * compliance with the License.
 * 
 * You can obtain a copy of the license at
 * http://www.opensource.org/licenses/cddl1.txt See the License for the specific
 * language governing permissions and limitations under the License.
 * 
 * When distributing Covered Code, include this CDDL HEADER in each file and
 * include the License file at http://www.opensource.org/licenses/cddl1.txt If
 * applicable, add the following below this CDDL HEADER, with the fields
 * enclosed by brackets "[]" replaced with your own identifying information:
 * Portions Copyright [yyyy] [name of copyright owner]
 */

package org.restlet.example.book.restlet.ch9;

import java.io.File;

import org.restlet.Component;
import org.restlet.Context;
import org.restlet.Directory;
import org.restlet.Restlet;
import org.restlet.Router;
import org.restlet.data.ChallengeScheme;
import org.restlet.data.LocalReference;
import org.restlet.data.Protocol;
import org.restlet.example.book.restlet.ch9.dao.DAOFactory;
import org.restlet.example.book.restlet.ch9.resources.ContactResource;
import org.restlet.example.book.restlet.ch9.resources.ContactsResource;
import org.restlet.example.book.restlet.ch9.resources.FeedResource;
import org.restlet.example.book.restlet.ch9.resources.FeedsResource;
import org.restlet.example.book.restlet.ch9.resources.MailResource;
import org.restlet.example.book.restlet.ch9.resources.MailRootResource;
import org.restlet.example.book.restlet.ch9.resources.MailboxResource;
import org.restlet.example.book.restlet.ch9.resources.MailboxesResource;
import org.restlet.example.book.restlet.ch9.resources.MailsResource;
import org.restlet.example.book.restlet.ch9.resources.UserResource;
import org.restlet.example.book.restlet.ch9.resources.UsersResource;

import com.db4o.Db4o;
import com.db4o.config.Configuration;

/**
 * The main Web application.
 */
public class Application extends org.restlet.Application {

    public static void main(String... args) throws Exception {
        // Create a component with an HTTP server connector
        Component component = new Component();
        component.getServers().add(Protocol.HTTP, 8585);
        component.getClients().add(Protocol.FILE);
        // Attach the application to the default host and start it
        component.getDefaultHost().attach("/rmep",
                new Application(component.getContext()));
        component.start();
    }

    /** DAO objects factory. */
    private DAOFactory daoFactory;

    /** Freemarker configuration object. */
    private freemarker.template.Configuration fmc;

    public Application(Context context) {
        super(context);
        /** Open and keep the db4o object container. */
        Configuration config = Db4o.configure();
        config.updateDepth(2);
        config.activationDepth(10);
        this.daoFactory = new DAOFactory(Db4o.openFile(System
                .getProperty("user.home")
                + File.separator + "rmep.dbo"));
        // Check that at least one administrator exists in the database.
        this.daoFactory.getUserDAO().initAdmin();

        try {
            File templateDir = new File(
                    "D:\\alaska\\forge\\build\\swc\\restlet\\trunk\\modules\\org.restlet.example\\src\\org\\restlet\\example\\book\\restlet\\ch9\\web\\tmpl");
            fmc = new freemarker.template.Configuration();
            fmc.setDirectoryForTemplateLoading(templateDir);
        } catch (Exception e) {
            getLogger().severe("Erreur config FreeMarker");
            e.printStackTrace();
        }
    }

    @Override
    public Restlet createRoot() {
        Router router = new Router(getContext());

        RmepGuard guard = new RmepGuard(getContext(),
                ChallengeScheme.HTTP_BASIC, "rmep", daoFactory);

        // Add a route for the MailRoot resource
        router.attachDefault(MailRootResource.class);

        Directory imgDirectory = new Directory(
                getContext(),
                LocalReference
                        .createFileReference("D:\\alaska\\forge\\build\\swc\\restlet\\trunk\\modules\\org.restlet.example\\src\\org\\restlet\\example\\book\\restlet\\ch9\\web\\images"));
        // Add a route for the image resources
        router.attach("/images", imgDirectory);

        Directory cssDirectory = new Directory(
                getContext(),
                LocalReference
                        .createFileReference("D:\\alaska\\forge\\build\\swc\\restlet\\trunk\\modules\\org.restlet.example\\src\\org\\restlet\\example\\book\\restlet\\ch9\\web\\stylesheets"));
        // Add a route for the CSS resources
        router.attach("/stylesheets", cssDirectory);

        // Add a route for a Mailboxes resource
        router.attach("/mailboxes", MailboxesResource.class);

        // Add a route for a Mailbox resource
        router.attach("/mailboxes/{mailboxId}", MailboxResource.class);

        // Add a route for a Contacts resource
        router
                .attach("/mailboxes/{mailboxId}/contacts",
                        ContactsResource.class);

        // Add a route for a Contact resource
        router.attach("/mailboxes/{mailboxId}/contacts/{contactId}",
                ContactResource.class);

        // Add a route for a Mails resource
        router.attach("/mailboxes/{mailboxId}/mails", MailsResource.class);

        // Add a route for a Mail resource
        router.attach("/mailboxes/{mailboxId}/mails/{mailId}",
                MailResource.class);

        // Add a route for a Feeds resource
        router.attach("/mailboxes/{mailboxId}/feeds", FeedsResource.class);

        // Add a route for a Feed resource
        router.attach("/mailboxes/{mailboxId}/feeds/{feedId}",
                FeedResource.class);

        // Add a route for a Users resource
        router.attach("/users", UsersResource.class);

        // Add a route for a User resource
        router.attach("/users/{userId}", UserResource.class);

        // Secure the whole application.
        guard.setNext(router);
        return guard;
    }

    /**
     * Returns the DAO factory.
     * 
     * @return the DAO factory.
     */
    public DAOFactory getDAOFactory() {
        return this.daoFactory;
    }

    /**
     * Returns the freemarker configuration object.
     * 
     * @return the freemarker configuration object.
     */
    public freemarker.template.Configuration getFmc() {
        return this.fmc;
    }

}
