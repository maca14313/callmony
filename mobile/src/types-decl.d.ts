declare module "react-native-immediate-phone-call" {
  const RNImmediatePhoneCall: {
    immediatePhoneCall: (phoneNumber: string) => void;
  };
  export default RNImmediatePhoneCall;
}

declare module "react-native-call-log" {
  interface RawCallLogEntry {
    phoneNumber?: string;
    cachedName?: string;
    callType?: string | number;
    callDayTime?: string;
    callDate?: string | number;
    duration?: string | number;
    type?: string;
    timestamp?: string | number;
  }
  const CallLog: {
    load: (limit: number) => Promise<RawCallLogEntry[]>;
    loadAll: () => Promise<RawCallLogEntry[]>;
  };
  export default CallLog;
}

declare module "react-native-vector-icons/MaterialCommunityIcons" {
  import * as React from "react";
  import type { TextProps, TextStyle } from "react-native";

  export interface IconProps {
    name: string;
    size?: number;
    color?: string | number;
    style?: TextStyle;
    allowFontScaling?: boolean;
    [key: string]: unknown;
  }

  const Icon: React.FC<IconProps & Omit<TextProps, "style">> & {
    getImageSource?: (
      name: string,
      size?: number,
      color?: string
    ) => Promise<{ uri: string; scale: number }>;
  };

  export default Icon;
}
