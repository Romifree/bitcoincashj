/*
 * Copyright by the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.bitcoincashj.walletfx.application;

import javafx.application.Application;
import javafx.stage.Stage;

/**
 * A delegate that implements JavaFX {@link Application}
 */
public interface AppDelegate {
    /**
     * Implement this method if you have code to run during {@link Application#init()} or
     * if you need a reference to the actual {@code Application object}
     * @param application a reference to the actual {@code Application} object
     * @throws Exception something bad happened
     */
    default void init(Application application) throws Exception {
    }
    void start(Stage primaryStage) throws Exception;
    void stop() throws Exception;
}