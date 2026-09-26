package guessmarket.server.api;

import guessmarket.server.ServerApplication;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

import java.io.IOException;
import java.io.InputStream;

@WebServlet("/api/events/upload")
@MultipartConfig(maxFileSize = 10 * 1024 * 1024, maxRequestSize = 11 * 1024 * 1024)
public final class EventUploadServlet extends ApiServlet {
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Part file;
        try {
            file = request.getPart("file");
        } catch (ServletException | IllegalStateException error) {
            throw new ApiException(400, "INVALID_UPLOAD", "Request must contain one multipart XML file");
        }
        if (file == null || file.getSize() == 0) {
            throw new ApiException(400, "NO_FILE", "No XML file was supplied");
        }
        String filename = file.getSubmittedFileName();
        if (filename == null || !filename.toLowerCase(java.util.Locale.ROOT).endsWith(".xml")) {
            throw new ApiException(415, "UNSUPPORTED_FILE", "Uploaded file must use the .xml extension");
        }
        EventUploadService uploads = new EventUploadService(
                ServerApplication.requireState(request.getServletContext()));
        try (InputStream input = file.getInputStream()) {
            JsonSupport.write(response, HttpServletResponse.SC_CREATED,
                    uploads.upload(SessionSupport.token(request), input));
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        rejectUnsupportedMethod(response);
    }
}
