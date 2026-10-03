/****************************************************************************
 * cocos2dx 3.x Downloader 的 Java 桥。
 *
 * 原版（cocos2d-x 3.17.2）依赖 com.loopj.android.http（Apache HttpClient），
 * 本项目不引入该第三方库（许可与体积都不合适）。这里用 HttpURLConnection
 * 重写等价实现，对外方法签名与原版逐字一致：
 *
 *   static Cocos2dxDownloader createDownloader(int, int, String, int)
 *   static void createTask(Cocos2dxDownloader, int, String, String)
 *   static void cancelAllRequests(Cocos2dxDownloader)
 *
 * libgame.so（Kirikiroid2 主库）通过 JNI 反射按这些签名取 method id，
 * 改签名会导致 native 侧 getStaticMethodID 直接崩溃。
 ****************************************************************************/

package org.cocos2dx.lib;

import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class Cocos2dxDownloader {
    private static final String TAG = "Cocos2dxDownloader";

    private int _id;
    private int _timeoutInSeconds;
    private String _tempFileNameSufix;
    private int _countOfMaxProcessingTasks;
    private final HashMap<Integer, DownloadTask> _taskMap = new HashMap<Integer, DownloadTask>();
    private final Queue<Runnable> _taskQueue = new LinkedList<Runnable>();
    private int _runningTaskCount = 0;
    private static final HashMap<String, Boolean> _resumingSupport = new HashMap<String, Boolean>();
    private final ExecutorService _executor = Executors.newCachedThreadPool();

    private static void logD(String msg) {
        Log.d(TAG, msg);
    }

    void onProgress(final int id, final long downloadBytes, final long downloadNow, final long downloadTotal) {
        DownloadTask task = _taskMap.get(id);
        if (null != task) {
            task.bytesReceived = downloadBytes;
            task.totalBytesReceived = downloadNow;
            task.totalBytesExpected = downloadTotal;
        }
        Cocos2dxHelper.runOnGLThread(new Runnable() {
            @Override
            public void run() {
                nativeOnProgress(_id, id, downloadBytes, downloadNow, downloadTotal);
            }
        });
    }

    public void onStart(int id) {
        DownloadTask task = _taskMap.get(id);
        if (null != task) {
            task.resetStatus();
        }
    }

    public void onFinish(final int id, final int errCode, final String errStr, final byte[] data) {
        DownloadTask task = _taskMap.remove(id);
        if (null == task) return;
        final byte[] payload = data;
        Cocos2dxHelper.runOnGLThread(new Runnable() {
            @Override
            public void run() {
                nativeOnFinish(_id, id, errCode, errStr, payload);
            }
        });
    }

    public static void setResumingSupport(String host, Boolean support) {
        _resumingSupport.put(host, support);
    }

    public static Cocos2dxDownloader createDownloader(int id, int timeoutInSeconds, String tempFileNameSufix, int countOfMaxProcessingTasks) {
        Cocos2dxDownloader downloader = new Cocos2dxDownloader();
        downloader._id = id;
        downloader._timeoutInSeconds = timeoutInSeconds;
        downloader._tempFileNameSufix = tempFileNameSufix;
        downloader._countOfMaxProcessingTasks = countOfMaxProcessingTasks;
        return downloader;
    }

    public static void createTask(final Cocos2dxDownloader downloader, int id_, String url_, String path_) {
        final int id = id_;
        final String url = url_;
        final String path = path_;

        Runnable taskRunnable = new Runnable() {
            @Override
            public void run() {
                final DownloadTask task = new DownloadTask();
                if (0 == path.length()) {
                    // data task：整包下载到内存
                    downloader._taskMap.put(id, task);
                    downloader.execute(id, new Runnable() {
                        @Override
                        public void run() {
                            byte[] data = null;
                            String errStr = null;
                            try {
                                HttpURLConnection conn = downloader.open(url);
                                try {
                                    data = readAll(conn);
                                } finally {
                                    conn.disconnect();
                                }
                            } catch (Exception e) {
                                errStr = e.toString();
                            }
                            task.data = data;
                            downloader.onFinish(id, null == errStr ? 0 : -1, errStr, data);
                            downloader.runNextTaskIfExists();
                        }
                    });
                    return;
                }

                do {
                    if (0 == path.length()) break;

                    String domain;
                    try {
                        URI uri = new URI(url);
                        domain = uri.getHost();
                    } catch (URISyntaxException e) {
                        break;
                    }
                    final String host = domain.startsWith("www.") ? domain.substring(4) : domain;

                    Boolean supportResuming = false;
                    Boolean requestHeader = true;
                    if (_resumingSupport.containsKey(host)) {
                        supportResuming = _resumingSupport.get(host);
                        requestHeader = false;
                    }

                    if (requestHeader) {
                        // 先 HEAD 探 Accept-Ranges，再落到真实下载
                        downloader._taskMap.put(id, task);
                        downloader.execute(id, new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    HttpURLConnection conn = downloader.open(url);
                                    try {
                                        boolean acceptRanges = false;
                                        java.util.Map<String, List<String>> headers = conn.getHeaderFields();
                                        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                                            if (null == entry.getKey()) continue;
                                            if (!"accept-ranges".equalsIgnoreCase(entry.getKey())) continue;
                                            List<String> values = entry.getValue();
                                            if (null != values) {
                                                for (String v : values) {
                                                    if ("bytes".equalsIgnoreCase(v)) acceptRanges = true;
                                                }
                                            }
                                        }
                                        setResumingSupport(host, acceptRanges);
                                    } finally {
                                        conn.disconnect();
                                    }
                                } catch (Exception e) {
                                    downloader.onFinish(id, -1, e.toString(), null);
                                    downloader.runNextTaskIfExists();
                                    return;
                                }
                                createTask(downloader, id, url, path);
                            }
                        });
                        break;
                    }

                    // file task
                    File tempFile = new File(path + downloader._tempFileNameSufix);
                    if (tempFile.isDirectory()) break;

                    File parent = tempFile.getParentFile();
                    if (null != parent && !parent.isDirectory() && !parent.mkdirs()) break;

                    File finalFile = new File(path);
                    if (finalFile.isDirectory()) break;

                    downloader._taskMap.put(id, task);
                    downloader.execute(id, new FileTaskRunner(downloader, id, url, tempFile, finalFile, supportResuming));
                } while (false);

                if (null == downloader._taskMap.get(id)) {
                    final String errStr = "Can't create DownloadTask for " + url;
                    downloader.onFinish(id, 0, errStr, null);
                }
            }
        };
        downloader.enqueueTask(taskRunnable);
    }

    private void execute(final int id, final Runnable worker) {
        DownloadTask task = _taskMap.get(id);
        if (null == task) {
            task = new DownloadTask();
            _taskMap.put(id, task);
        }
        task.future = _executor.submit(new Runnable() {
            @Override
            public void run() {
                onStart(id);
                worker.run();
            }
        });
    }

    private HttpURLConnection open(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        int timeout = _timeoutInSeconds > 0 ? _timeoutInSeconds * 1000 : 30000;
        conn.setConnectTimeout(timeout);
        conn.setReadTimeout(timeout);
        conn.setInstanceFollowRedirects(true);
        // 关掉 gzip：解压会让进度统计失真，原版 loopj 侧同样按未解压长度处理
        conn.setRequestProperty("Accept-Encoding", "identity");
        return conn;
    }

    private static byte[] readAll(HttpURLConnection conn) throws IOException {
        InputStream in = conn.getInputStream();
        try {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while (-1 != (n = in.read(buf))) bos.write(buf, 0, n);
            return bos.toByteArray();
        } finally {
            try { in.close(); } catch (IOException e) { /* ignore */ }
        }
    }


    private static class FileTaskRunner implements Runnable {
        private final Cocos2dxDownloader downloader;
        private final int _id;
        private final String _url;
        private final File _tempFile;
        private final File _finalFile;
        private final Boolean _supportResuming;

        FileTaskRunner(Cocos2dxDownloader downloader, int id, String url, File tempFile, File finalFile, Boolean supportResuming) {
            this.downloader = downloader;
            this._id = id;
            this._url = url;
            this._tempFile = tempFile;
            this._finalFile = finalFile;
            this._supportResuming = supportResuming;
        }

        @Override
        public void run() {
            String errStr = null;
            long fileLen = _tempFile.length();
            try {
                if (fileLen > 0) {
                    if (_supportResuming) {
                        // 续传
                    } else {
                        // 去掉上一次的半包内容
                        try {
                            PrintWriter writer = new PrintWriter(_tempFile);
                            writer.print("");
                            writer.close();
                        } catch (FileNotFoundException e) { /* not found then nothing to do */ }
                        fileLen = 0;
                    }
                }
                HttpURLConnection conn = downloader.open(_url);
                if (fileLen > 0) conn.setRequestProperty("Range", "bytes=" + fileLen + "-");
                int code = conn.getResponseCode();
                InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
                FileOutputStream out = new FileOutputStream(_tempFile, fileLen > 0);
                try {
                    long totalExpected = conn.getContentLength();
                    if (totalExpected > 0) totalExpected += fileLen;
                    while (totalExpected <= 0) {
                        // 某些服务器不给 Content-Length：先按已下载量估算，保持进度回调不倒退
                        totalExpected = -1;
                        break;
                    }
                    byte[] buf = new byte[8192];
                    int n;
                    long done = 0;
                    while (-1 != (n = in.read(buf))) {
                        out.write(buf, 0, n);
                        done += n;
                        downloader.onProgress(_id, n, done, totalExpected);
                    }
                    out.flush();
                    if (code >= 400) {
                        errStr = "HTTP " + code + " for " + _url;
                    }
                } finally {
                    try { in.close(); } catch (IOException e) { /* ignore */ }
                    try { out.close(); } catch (IOException e) { /* ignore */ }
                    conn.disconnect();
                }
            } catch (Exception e) {
                errStr = e.toString();
            }

            if (null == errStr) {
                errStr = commitFinalFile();
            }
            downloader.onFinish(_id, null == errStr ? 0 : -1, errStr, null);
            downloader.runNextTaskIfExists();
        }

        private String commitFinalFile() {
            try {
                if (_finalFile.exists()) {
                    if (_finalFile.isDirectory()) return "Dest file is directory:" + _finalFile.getAbsolutePath();
                    if (!_finalFile.delete()) return "Can't remove old file:" + _finalFile.getAbsolutePath();
                }
                if (!_tempFile.renameTo(_finalFile)) return "Can't rename temp file to:" + _finalFile.getAbsolutePath();
                return null;
            } catch (Exception e) {
                return e.toString();
            }
        }
    }

    public static void cancelAllRequests(final Cocos2dxDownloader downloader) {
        if (null == downloader) return;
        Cocos2dxHelper.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                List<Integer> keys = new ArrayList<Integer>(downloader._taskMap.keySet());
                for (Integer key : keys) {
                    DownloadTask task = downloader._taskMap.remove(key);
                    if (null != task && null != task.future) task.future.cancel(true);
                }
                downloader._executor.shutdownNow();
                logD("cancelAllRequests: " + keys.size() + " task(s) cancelled");
            }
        });
    }

    public void enqueueTask(Runnable taskRunnable) {
        synchronized (_taskQueue) {
            if (_runningTaskCount < _countOfMaxProcessingTasks || 0 == _countOfMaxProcessingTasks) {
                Cocos2dxHelper.getActivity().runOnUiThread(taskRunnable);
                _runningTaskCount++;
            } else {
                _taskQueue.add(taskRunnable);
            }
        }
    }

    public void runNextTaskIfExists() {
        synchronized (_taskQueue) {
            Runnable taskRunnable = Cocos2dxDownloader.this._taskQueue.poll();
            if (taskRunnable != null) {
                Cocos2dxHelper.getActivity().runOnUiThread(taskRunnable);
            } else {
                _runningTaskCount--;
                if (_runningTaskCount < 0) _runningTaskCount = 0;
            }
        }
    }

    native void nativeOnProgress(int id, int taskId, long dl, long dlnow, long dltotal);

    native void nativeOnFinish(int id, int taskId, int errCode, String errStr, final byte[] data);

    static class DownloadTask {
        Future<?> future;
        // progress
        long bytesReceived;
        long totalBytesReceived;
        long totalBytesExpected;
        byte[] data;

        DownloadTask() {
            resetStatus();
        }

        void resetStatus() {
            bytesReceived = 0;
            totalBytesReceived = 0;
            totalBytesExpected = 0;
            data = null;
        }
    }
}
