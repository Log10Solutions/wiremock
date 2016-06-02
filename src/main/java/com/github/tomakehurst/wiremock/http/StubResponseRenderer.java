/*
 * Copyright (C) 2011 Thomas Akehurst
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.tomakehurst.wiremock.http;

import com.github.tomakehurst.wiremock.common.BinaryFile;
import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.global.GlobalSettingsHolder;
import com.google.common.base.Optional;

import static com.github.tomakehurst.wiremock.http.Response.response;

public class StubResponseRenderer implements ResponseRenderer {
	
	private final FileSource fileSource;
	private final GlobalSettingsHolder globalSettingsHolder;
	private final ProxyResponseRenderer proxyResponseRenderer;
	private final FileSource extraFolder;
	private final FileSource extraFolder2;

    public StubResponseRenderer(FileSource fileSource,
                                GlobalSettingsHolder globalSettingsHolder,
                                ProxyResponseRenderer proxyResponseRenderer) {
        this.fileSource = fileSource;
        this.globalSettingsHolder = globalSettingsHolder;
        this.proxyResponseRenderer = proxyResponseRenderer;
        this.extraFolder = null;
        this.extraFolder2 = null;
    }
    
    public StubResponseRenderer(FileSource fileSource,
					            GlobalSettingsHolder globalSettingsHolder,
					            ProxyResponseRenderer proxyResponseRenderer,
					            FileSource extraFolder) {
		this.fileSource = fileSource;
		this.globalSettingsHolder = globalSettingsHolder;
		this.proxyResponseRenderer = proxyResponseRenderer;
		this.extraFolder = extraFolder;
        this.extraFolder2 = null;
	}
    
    public StubResponseRenderer(FileSource fileSource,
					            GlobalSettingsHolder globalSettingsHolder,
					            ProxyResponseRenderer proxyResponseRenderer,
					            FileSource extraFolder,
					            FileSource extraFolder2) {
		this.fileSource = fileSource;
		this.globalSettingsHolder = globalSettingsHolder;
		this.proxyResponseRenderer = proxyResponseRenderer;
		this.extraFolder = extraFolder;
		this.extraFolder2 = extraFolder2;
	}

	@Override
	public Response render(ResponseDefinition responseDefinition) {
		if (!responseDefinition.wasConfigured()) {
			return Response.notConfigured();
		}
		
		addDelayIfSpecifiedGloballyOrIn(responseDefinition);
		if (responseDefinition.isProxyResponse()) {
	    	return proxyResponseRenderer.render(responseDefinition);
	    } else {
	    	return renderDirectly(responseDefinition);
	    }
	}
	
	private Response renderDirectly(ResponseDefinition responseDefinition) {
        Response.Builder responseBuilder = response()
                .status(responseDefinition.getStatus())
                .headers(responseDefinition.getHeaders())
                .fault(responseDefinition.getFault());

		if (responseDefinition.specifiesBodyFile()) {
			byte[] bodyContent = null;
			boolean bodyContentRead = false;
			if (extraFolder2 != null) {
				try {
					BinaryFile bodyFile = extraFolder2.getBinaryFileNamed(responseDefinition.getBodyFileName());
					bodyContent = bodyFile.readContents();
					bodyContentRead = true;
				} catch (Exception e) {
					//Impossible to reach the file in the extra folder
				}
			}
			if (!bodyContentRead && extraFolder != null) {
				try {
					BinaryFile bodyFile = extraFolder.getBinaryFileNamed(responseDefinition.getBodyFileName());
					bodyContent = bodyFile.readContents();
					bodyContentRead = true;
				} catch (Exception e) {
					//Impossible to reach the file in the extra folder
				}
			}
			if (!bodyContentRead) {
				FileSource bodyFolderHACK = fileSource.child("..").child("..").child("..").child("expectationForRecordBody");
				if (bodyFolderHACK.exists()) {
					BinaryFile bodyFile = bodyFolderHACK.getBinaryFileNamed(responseDefinition.getBodyFileName());
					bodyContent = bodyFile.readContents();
				} else {
					BinaryFile bodyFile = fileSource.getBinaryFileNamed(responseDefinition.getBodyFileName());
					bodyContent = bodyFile.readContents();
				}
				bodyContentRead = true;
			}
			
            responseBuilder.body(bodyContent);
		} else if (responseDefinition.specifiesBodyContent()) {
            if(responseDefinition.specifiesBinaryBodyContent()) {
                responseBuilder.body(responseDefinition.getByteBody());
            } else {
                responseBuilder.body(responseDefinition.getBody());
            }
		}

        return responseBuilder.build();
	}
	
    private void addDelayIfSpecifiedGloballyOrIn(ResponseDefinition response) {
    	Optional<Integer> optionalDelay = getDelayFromResponseOrGlobalSetting(response);
        if (optionalDelay.isPresent()) {
	        try {
	            Thread.sleep(optionalDelay.get());
	        } catch (InterruptedException e) {
	            Thread.currentThread().interrupt();
	        }
	    }
    }
    
    private Optional<Integer> getDelayFromResponseOrGlobalSetting(ResponseDefinition response) {
    	Integer delay = response.getFixedDelayMilliseconds() != null ?
    			response.getFixedDelayMilliseconds() :
    			globalSettingsHolder.get().getFixedDelay();
    	
    	return Optional.fromNullable(delay);
    }
}
