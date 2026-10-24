/* Email composer with attachments (own native module — CallMonyEmail).
   Opens the user's email app with the files attached and the recipient
   pre-filled. No SMTP credentials, no silent sending: the user presses
   Send. Works together with lib/backup.ts for the auto email sync. */

import { NativeModules, Platform } from "react-native";

interface CallMonyEmailNative {
  compose(to: string, subject: string, body: string, files: string[]): Promise<boolean>;
}

const EmailNative = NativeModules.CallMonyEmail as CallMonyEmailNative | undefined;

/**
 * Open the email composer with optional attachments.
 * @returns true when the composer was opened successfully.
 */
export const composeEmail = async (opts: {
  to?: string;
  subject: string;
  body: string;
  files?: string[];
}): Promise<boolean> => {
  if (Platform.OS !== "android" || !EmailNative) return false;
  try {
    return await EmailNative.compose(opts.to ?? "", opts.subject, opts.body, opts.files ?? []);
  } catch {
    return false;
  }
};
