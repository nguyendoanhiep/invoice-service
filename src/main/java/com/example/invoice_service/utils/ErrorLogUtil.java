package com.example.invoice_service.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;

@Slf4j
public final class ErrorLogUtil {

    private ErrorLogUtil() {
    }

    public static void log(Exception ex) {
        log.error(
                """
                        ===== SYSTEM ERROR =====
                        Error    : {}
                        Message  : {}
                        ========================
                        """,
                ex.getClass().getName(),
                ex.getMessage(),
                ex
        );
    }

    private static final int MAX_LENGTH = 1999;
    private static final int HEAD_LENGTH = 1200;
    private static final int TAIL_LENGTH = 700;

    public static String buildTraceForDb(Throwable e) {
        if (e == null) {
            return null;
        }

        String fullTrace = ExceptionUtils.getStackTrace(e);

        // Nếu vừa khít thì trả luôn
        if (fullTrace.length() <= MAX_LENGTH) {
            return fullTrace;
        }

        // Lấy root cause (nếu có)
        Throwable root = ExceptionUtils.getRootCause(e);
        if (root != null && root != e) {
            String rootTrace = ExceptionUtils.getStackTrace(root);

            if (rootTrace.length() <= MAX_LENGTH) {
                return "[ROOT CAUSE ONLY]\n" + rootTrace;
            }
        }

        // Cắt thông minh: đầu + cuối (giữ Caused by)
        String head = fullTrace.substring(0, HEAD_LENGTH);
        String tail = fullTrace.substring(fullTrace.length() - TAIL_LENGTH);

        return head
                + "\n...\n"
                + tail;
    }
}