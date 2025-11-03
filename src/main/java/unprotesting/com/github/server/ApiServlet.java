
package unprotesting.com.github.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collection;
import unprotesting.com.github.data.Database;
import unprotesting.com.github.data.Shop;

public class ApiServlet extends HttpServlet {

    private final Gson gson = new GsonBuilder().create();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        String path = req.getPathInfo();
        if (path == null) {
            path = "/";
        }

        switch (path) {
            case "/shops":
                handleShops(resp);
                break;
            default:
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                resp.getWriter().println(gson.toJson(new ErrorResponse("Not Found")));
                break;
        }
    }

    private void handleShops(HttpServletResponse resp) throws IOException {
        try {
            Database.acquireReadLock();
            Collection<Shop> shops = Database.get().getShops().values();
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.getWriter().println(gson.toJson(shops));
        } finally {
            Database.releaseReadLock();
        }
    }

    private static class ErrorResponse {
        private final String error;

        public ErrorResponse(String error) {
            this.error = error;
        }
    }
}
