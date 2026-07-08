package org.vaadin.addons.mygroup;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.VaadinServiceInitListener;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.theme.aura.Aura;
import com.vaadin.flow.theme.lumo.Lumo;

/**
 * A helper to allow changing the theme in the test views with a URL parameter.
 * E.g. ?theme=aura changes sessions default theme to aura.
 */
public class ThemeChangeSupport
        implements VaadinServiceInitListener {

    @Override
    public void serviceInit(ServiceInitEvent serviceInitEvent) {
        serviceInitEvent.getSource().addUIInitListener( uiEvt -> {
            VaadinRequest request = VaadinRequest.getCurrent();

            // Note, as there is the "react router" and it "two-request init", this does not work!
            /*
            String parameter = request.getParameter("theme");
             */

            // But Vaadin does some hack and sends the original query string as "query" query parameter...
            String realQueryParameters = request.getParameter("query");

            String parameter = "";
            String[] split = realQueryParameters.split("=");
            for(int i = 0; i < split.length; i++) {
                if(split[i].equals("theme")) {
                    parameter = split[i+1];
                    break;
                }
            }

            VaadinSession session = uiEvt.getUI().getSession();
            if("lumo".equals(parameter)) {
                session.setAttribute("theme", Lumo.class);
            } else if ("aura".equals(parameter)) {
                session.setAttribute("theme", Aura.class);
            } else if(!parameter.isEmpty()) {
                // anything else (themeless)
                session.setAttribute("theme", "void");
            }

            Object theme = session.getAttribute("theme");
            if(theme == null) {
                theme = Lumo.class;
            }

            UI ui = uiEvt.getUI();

            if(theme == Lumo.class) {
                ui.getPage().addStyleSheet(Lumo.STYLESHEET);
                ui.getPage().addStyleSheet(Lumo.UTILITY_STYLESHEET);
            } else if (theme == Aura.class) {
                ui.getPage().addStyleSheet(Aura.STYLESHEET);
            }

        });
    }

}