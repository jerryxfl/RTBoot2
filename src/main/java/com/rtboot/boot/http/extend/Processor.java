package com.rtboot.boot.http.extend;

import com.google.gson.Gson;
import com.rtboot.boot.http.bean.RequestHeader;
import com.rtboot.boot.http.bean.RequestUrl;
import com.rtboot.boot.http.enums.RequestProtocol;
import com.rtboot.boot.http.enums.RequestVersion;
import com.rtboot.boot.http.model.Request;
import com.rtboot.boot.http.model.Response;
import com.rtboot.boot.http.utils.StringParser;
import com.rtboot.boot.rtboot.utils.Logger;

import java.io.*;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

public class Processor {
    private static final Gson gson = new Gson();

    public static Request processRequest(Socket socket){
        try {
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            boolean isProtocolLine = true;
            RequestProtocol requestProtocol = null;
            RequestUrl requestUrl = null;
            RequestVersion requestVersion = null;
            RequestHeader requestHeader = null;
            Map<String,Object> headers = new HashMap<>();
            while (true){
                String line = bufferedReader.readLine();
                if (isProtocolLine){
                    if (line.isEmpty()){
                        return null;
                    }
                    String[] split = line.split(" ");
                    for (int i = 0; i < split.length; i++) {
                        String content = split[i];
                        if (i==0){
                            //method
                            requestProtocol = StringParser.parserRequestProtocol(content);
                        }else  if(i==1){
                            //link
                            requestUrl = new RequestUrl(content);
                        }else if(i==2){
                            //version
                            requestVersion = StringParser.parserRequestVersion(content);
                        }
                    }
                    isProtocolLine = false;
                }else if (line.contains(":")){
                    String[] strings = parseHeader(line);
                    if (strings!=null){
                        headers.put(strings[0],strings[1]);
                    }
                }else  if (line.isEmpty()){
                    break;
                }
            }
            requestHeader = new RequestHeader(headers);
            if(requestProtocol != null && requestVersion != null && requestUrl != null){
                return new Request(requestProtocol,requestVersion,requestUrl,requestHeader);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static String[] parseHeader(String content){
        if (content.isEmpty()){
            return null;
        }
        if(!content.contains(":")){
            return null;
        }
        String[] split = content.split(":");
        return StringParser.parseStringKV(split);
    }


    //todo
    public static void processResponse(Socket socket,Request request, Response response){
        Logger.i("request:"+request.getRequestUrl().getPath()+",start response:"+response);

        Map<String,Object> header = new HashMap<>();
        header.put("Content-Length:",response.getContentLength());
        header.put("Content-Type:","text/plain");

        try{
            OutputStream outputStream = socket.getOutputStream();
            BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(outputStream);
//            PrintWriter printWriter = new PrintWriter(outputStream);
            String responseLine = request.getRequestVersion().getVersion()+" "+ response.getCode() + " "+response.getMsg() + "\r\n";
            bufferedOutputStream.write(responseLine.getBytes());
            for (Map.Entry<String, Object> map : header.entrySet()) {
                String head = map.getKey() + map.getValue() + "\r\n";
                bufferedOutputStream.write(head.getBytes());
            }
            String endLine = "\r\n";
            bufferedOutputStream.write(endLine.getBytes());
            if (response.getData()!=null){
                String s = gson.toJson(response.getData());
                bufferedOutputStream.write(s.getBytes());
            }
            bufferedOutputStream.flush();
            bufferedOutputStream.close();
            outputStream.close();
            socket.close();
        }catch (Exception e){
            e.printStackTrace();
        }
    }
}
