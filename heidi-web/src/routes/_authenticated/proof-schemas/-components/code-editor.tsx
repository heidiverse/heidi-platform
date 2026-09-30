// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { validate_logic } from "jlc";
import * as monaco from "monaco-editor";
import { useEffect, useRef, useState } from "react";
import { registerLanguage } from "@/lib/hooks/aifc";
import "monaco-editor/language/json/monaco.contribution";

export function CodeEditor({
  validationLogic,
  setValidationLogic,
  setIsDirty,
}: {
  validationLogic: string;
  setValidationLogic: (v: string) => void;
  setIsDirty: (v: boolean) => void;
}) {
  const [isLogicValid, setIsLogicValid] = useState(true);
  const [errorMsg, setErrorMsg] = useState();
  const monacoEl = useRef(null);
  const editorRef = useRef<monaco.editor.IStandaloneCodeEditor>(undefined);

  useEffect(() => {
    if (monacoEl.current) {
      const newEditor = monaco.editor.create(monacoEl.current, {
        value: validationLogic,
        language: "aifc",
        automaticLayout: true,
        minimap: {
          enabled: false,
        },
      });
      registerLanguage();

      editorRef.current = newEditor;

      newEditor.onDidChangeModelContent(() => {
        const content = newEditor.getValue();
        setIsDirty(true);
        try {
          setIsLogicValid(true);
          validate_logic(content);
          setValidationLogic(content);
        } catch (error) {
          if (content === "") {
            setIsLogicValid(true);
            setValidationLogic(content);
          } else {
            // biome-ignore lint/suspicious/noExplicitAny: TODO: dont use any
            setErrorMsg(error as any);
            setIsLogicValid(false);
          }
          console.error(error);
        }
      });

      return () => newEditor.dispose();
    }
  }, []);

  return (
    <div className="relative -mx-4 h-full flex-1">
      <div ref={monacoEl} className="absolute inset-0" />
      {!isLogicValid && (
        <div className="absolute bottom-4 left-4 rounded bg-red-500 p-2 text-white">
          {errorMsg}
        </div>
      )}
    </div>
  );
}
