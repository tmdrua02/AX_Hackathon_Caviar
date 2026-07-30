import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "성분체크 — 병용 안전 점검 와이어프레임",
  description: "처방약·일반약·건강기능식품을 성분 단위로 비교하는 33개 화면의 인터랙티브 UX 와이어프레임",
  icons: {
    icon: "/favicon.svg",
    shortcut: "/favicon.svg",
  },
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="ko">
      <body>{children}</body>
    </html>
  );
}
