package com.coap.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;

/**
 * IO 工具类
 *
 * @author zhangyonghong
 * @date 2019.8.15
 */
public class IOUtil {

    private static Logger logger = LoggerFactory.getLogger(IOUtil.class);

    public static byte[] stream2Bytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int len;
        while ((len = inputStream.read(buf)) != -1) {
            baos.write(buf, 0, len);
        }
        return baos.toByteArray();
    }

    public static void write2File(byte[] bytes, String filePath) throws IOException {
        write2File(bytes, filePath, false);
    }

    /**
     * 将字节数组写到文件
     *
     * @param bytes    字节数组
     * @param filePath 文件路径
     */
    public static void write2File(byte[] bytes, String filePath, boolean append) throws IOException {
        File file = new File(filePath);
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        FileOutputStream fos = new FileOutputStream(new File(filePath), append);
        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        int len;
        byte[] buf = new byte[8192];
        try {
            while ((len = bais.read(buf)) != -1) {
                fos.write(buf, 0, len);
            }
            bais.close();
            fos.close();
        } catch (IOException e) {
            file.delete();
            throw e;
        }
    }

    /**
     * 从文件获取其内容的字节数组
     *
     * @param filePath 文件路径
     * @return 文件内容的字节数组
     */
    public static byte[] file2Bytes(String filePath) throws IOException {
        InputStream inputStream = new FileInputStream(filePath);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int len;
        byte[] buf = new byte[8192];
        while ((len = inputStream.read(buf)) != -1) {
            baos.write(buf, 0, len);
        }
        byte[] bytes = baos.toByteArray();
        inputStream.close();
        return bytes;
    }

}
