package com.github.lye.server;

import lombok.Getter;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import com.github.lye.TradeFlow;
import com.github.lye.config.Config;

import com.github.lye.config.settings.IPluginSettings;

import com.github.lye.util.TradeFlowLogger;

/**
 * The class for starting the web server.
 */
public class LocalServer {

    @Getter
    private static LocalServer instance;

    @Getter
    private Server server;

    private static IPluginSettings pluginSettings;
    private static TradeFlowLogger logger;
    private static TradeFlow plugin;

    public static void initialize(TradeFlow tradeFlow, IPluginSettings settings, TradeFlowLogger tradeFlowLogger) {
        plugin = tradeFlow;
        pluginSettings = settings;
        logger = tradeFlowLogger;
        instance = new LocalServer();
        if (pluginSettings.isWebServer()) instance.start();
    }

    /**
     * Start the integrated web server.
     */
    public void start() {
        server = new Server();
        String bindAddress = pluginSettings.getBindAddress();
        ServerConnector connector = new ServerConnector(server);
        connector.setHost(bindAddress);
        connector.setPort(pluginSettings.getPort());
        server.setConnectors(new Connector[] { connector });

        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");

        // Get API key from config if set
        String apiKey = plugin.getConfig().getString("api.key", "");

        // API servlet
        context.addServlet(new ServletHolder(new ApiServlet(
            plugin.getServices().get(com.github.lye.data.Database.class),
            plugin.getServices().get(TradeFlowLogger.class),
            apiKey,
            plugin
        )), "/api/*");

        // Default servlet for static files (serves as fallback)
        org.eclipse.jetty.servlet.DefaultServlet defaultServlet = new org.eclipse.jetty.servlet.DefaultServlet();
        ServletHolder staticHolder = new ServletHolder(defaultServlet);
        staticHolder.setInitParameter("resourceBase", plugin.getDataFolder().getAbsolutePath() + "/web");
        staticHolder.setInitParameter("dirAllowed", "false");
        context.addServlet(staticHolder, "/");

        // Add security headers filter
        context.addFilter(new FilterHolder(new SecurityHeadersFilter()), "/*", null);

        server.setHandler(context);

        try {
            server.start();
            logger.config("Local server started on " + bindAddress + ":" + pluginSettings.getPort());
        } catch (Exception e) {
            logger.severe("Failed to start local server!");
            logger.config(e.toString());
        }
    }

    /**
     * Stop the integrated web server.
     */
    public void stop() {
        try {
            server.stop();
        } catch (Exception e) {
            logger.severe("Failed to stop local server: " + e.getMessage());
        }
    }

}
