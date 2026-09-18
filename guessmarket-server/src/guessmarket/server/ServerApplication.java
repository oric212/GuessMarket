package guessmarket.server;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

@WebListener
public final class ServerApplication implements ServletContextListener {
    public static final String STATE_ATTRIBUTE = ServerState.class.getName();

    @Override
    public void contextInitialized(ServletContextEvent event) {
        event.getServletContext().setAttribute(STATE_ATTRIBUTE, new ServerState());
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        event.getServletContext().removeAttribute(STATE_ATTRIBUTE);
    }

    public static ServerState requireState(ServletContext context) {
        Object state = context.getAttribute(STATE_ATTRIBUTE);
        if (!(state instanceof ServerState serverState)) {
            throw new IllegalStateException("GuessMarket server state is not initialized");
        }
        return serverState;
    }
}
