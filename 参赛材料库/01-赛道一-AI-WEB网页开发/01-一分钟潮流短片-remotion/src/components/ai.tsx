import React from 'react';
import { AbsoluteFill, interpolate, useCurrentFrame } from 'remotion';
import { C, EASE, F, enter } from '../theme';

/**
 * AI 讲解片专用共享组件
 * 供 BackendAI / MiniAI 两支 60 秒片复用，保证版式与节奏一致
 */

/* ============ 1. 章节标题 ============ */

export const SectionTitle: React.FC<{
  kicker: string;
  title: string;
  sub?: string;
  from?: number;
  accent?: string;
  size?: number;
}> = ({ kicker, title, sub, from = 0, accent = C.cyan, size = 88 }) => {
  const frame = useCurrentFrame();
  const k = enter(frame, from, 14);
  const t = enter(frame, from + 6, 18);
  const s = enter(frame, from + 14, 18);

  return (
    <div style={{ position: 'absolute', left: 140, top: 96 }}>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 12,
          opacity: k,
          transform: `translateY(${(1 - k) * 10}px)`,
        }}
      >
        <div
          style={{
            width: 26,
            height: 2,
            background: accent,
            boxShadow: `0 0 14px ${accent}`,
          }}
        />
        <span
          style={{
            fontFamily: F.mono,
            fontSize: 19,
            letterSpacing: 5,
            color: accent,
            fontWeight: 700,
          }}
        >
          {kicker}
        </span>
      </div>

      <div
        style={{
          marginTop: 16,
          fontFamily: F.cn,
          fontSize: size,
          fontWeight: 900,
          letterSpacing: -2,
          lineHeight: 1.1,
          color: C.ink,
          opacity: t,
          transform: `translateY(${(1 - t) * 18}px)`,
          whiteSpace: 'nowrap',
        }}
      >
        {title}
      </div>

      {sub ? (
        <div
          style={{
            marginTop: 14,
            fontFamily: F.cn,
            fontSize: 30,
            fontWeight: 500,
            color: 'rgba(255,255,255,0.62)',
            opacity: s,
            transform: `translateY(${(1 - s) * 14}px)`,
            whiteSpace: 'nowrap',
          }}
        >
          {sub}
        </div>
      ) : null}
    </div>
  );
};

/* ============ 2. 代码块（SQL / JSON） ============ */

const KEYWORDS = [
  'SELECT', 'FROM', 'WHERE', 'AND', 'OR', 'LIMIT', 'ORDER', 'BY', 'GROUP',
  'LEFT', 'JOIN', 'ON', 'AS', 'DESC', 'ASC', 'COUNT', 'NOT', 'IN', 'IS', 'NULL',
];

const highlightLine = (line: string, accent: string): React.ReactNode => {
  // 先按字符串字面量切分，避免把引号里的内容当关键字
  const parts = line.split(/('[^']*')/g);
  return parts.map((part, i) => {
    if (part.startsWith("'") && part.endsWith("'")) {
      return (
        <span key={i} style={{ color: C.lime }}>
          {part}
        </span>
      );
    }
    // 关键字 / 数字
    const tokens = part.split(/(\b\d+\b)/g);
    return tokens.map((tk, j) => {
      const upper = tk.toUpperCase();
      if (KEYWORDS.includes(upper) && /[A-Za-z]/.test(tk)) {
        return (
          <span key={`${i}-${j}`} style={{ color: accent, fontWeight: 700 }}>
            {tk}
          </span>
        );
      }
      if (/^\d+$/.test(tk)) {
        return (
          <span key={`${i}-${j}`} style={{ color: C.amber }}>
            {tk}
          </span>
        );
      }
      return <span key={`${i}-${j}`}>{tk}</span>;
    });
  });
};

export const CodeBlock: React.FC<{
  lines: string[];
  from?: number;
  accent?: string;
  title?: string;
  width?: number;
  fontSize?: number;
  /** 需要额外强调的行号（从 0 开始） */
  emphasis?: number[];
}> = ({ lines, from = 0, accent = C.cyan, title, width = 900, fontSize = 24, emphasis = [] }) => {
  const frame = useCurrentFrame();

  return (
    <div
      style={{
        width,
        borderRadius: 18,
        border: `1px solid ${accent}33`,
        background: 'rgba(8,10,18,0.86)',
        boxShadow: `0 24px 80px rgba(0,0,0,0.55), inset 0 0 60px ${accent}0d`,
        overflow: 'hidden',
      }}
    >
      {/* 标题栏 */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          padding: '12px 18px',
          borderBottom: `1px solid ${C.line}`,
          background: 'rgba(255,255,255,0.03)',
        }}
      >
        {[C.warn, C.amber, C.lime].map((c, i) => (
          <div key={i} style={{ width: 10, height: 10, borderRadius: 5, background: `${c}99` }} />
        ))}
        <span
          style={{
            marginLeft: 10,
            fontFamily: F.mono,
            fontSize: 16,
            letterSpacing: 2,
            color: C.dim,
          }}
        >
          {title ?? 'sql'}
        </span>
      </div>

      {/* 代码行 */}
      <div style={{ padding: '18px 0 20px' }}>
        {lines.map((line, i) => {
          const p = enter(frame, from + i * 2, 10);
          const isEm = emphasis.includes(i);
          return (
            <div
              key={i}
              style={{
                display: 'flex',
                gap: 18,
                padding: '3px 22px',
                opacity: p,
                transform: `translateX(${(1 - p) * 16}px)`,
                background: isEm ? `${accent}12` : 'transparent',
                borderLeft: isEm ? `2px solid ${accent}` : '2px solid transparent',
              }}
            >
              <span
                style={{
                  width: 24,
                  textAlign: 'right',
                  fontFamily: F.mono,
                  fontSize: fontSize - 4,
                  color: C.dimmer,
                  flexShrink: 0,
                }}
              >
                {i + 1}
              </span>
              <span
                style={{
                  fontFamily: F.mono,
                  fontSize,
                  color: 'rgba(255,255,255,0.88)',
                  whiteSpace: 'pre',
                  lineHeight: 1.55,
                }}
              >
                {highlightLine(line, accent)}
              </span>
            </div>
          );
        })}
      </div>
    </div>
  );
};

/* ============ 3. SSE 事件流 ============ */

export type StreamEvent = {
  tag: string;
  text: string;
  color?: string;
};

export const EventStream: React.FC<{
  events: StreamEvent[];
  from?: number;
  interval?: number;
  width?: number;
  fontSize?: number;
}> = ({ events, from = 0, interval = 14, width = 820, fontSize = 21 }) => {
  const frame = useCurrentFrame();

  return (
    <div style={{ width, display: 'flex', flexDirection: 'column', gap: 10 }}>
      {events.map((ev, i) => {
        const p = enter(frame, from + i * interval, 10);
        const color = ev.color ?? C.cyan;
        return (
          <div
            key={i}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 14,
              padding: '11px 16px',
              borderRadius: 10,
              border: `1px solid ${color}30`,
              background: `${color}0d`,
              opacity: p,
              transform: `translateX(${(1 - p) * 22}px)`,
            }}
          >
            <span
              style={{
                fontFamily: F.mono,
                fontSize: fontSize - 3,
                fontWeight: 700,
                letterSpacing: 1,
                color,
                minWidth: 88,
              }}
            >
              {ev.tag}
            </span>
            <span
              style={{
                fontFamily: F.mono,
                fontSize,
                color: 'rgba(255,255,255,0.86)',
                whiteSpace: 'nowrap',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
              }}
            >
              {ev.text}
            </span>
          </div>
        );
      })}
    </div>
  );
};

/* ============ 4. 手机框（小程序画面） ============ */

export const PhoneFrame: React.FC<{
  children: React.ReactNode;
  width?: number;
  from?: number;
  accent?: string;
}> = ({ children, width = 380, from = 0, accent = C.cyan }) => {
  const frame = useCurrentFrame();
  const p = enter(frame, from, 18);
  const height = width * 2.05;

  return (
    <div
      style={{
        width,
        height,
        borderRadius: 46,
        padding: 9,
        background: 'linear-gradient(160deg, #23262F, #0C0E14)',
        boxShadow: `0 40px 120px rgba(0,0,0,0.7), 0 0 90px ${accent}22, inset 0 0 2px rgba(255,255,255,0.25)`,
        opacity: p,
        transform: `translateY(${(1 - p) * 40}px) scale(${0.96 + p * 0.04})`,
        flexShrink: 0,
      }}
    >
      <div
        style={{
          position: 'relative',
          width: '100%',
          height: '100%',
          borderRadius: 38,
          overflow: 'hidden',
          background: 'linear-gradient(180deg, #0B1220, #070A12 60%)',
        }}
      >
        {/* 状态栏 + 刘海 */}
        <div
          style={{
            height: 44,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            padding: '0 22px',
            fontFamily: F.mono,
            fontSize: 13,
            color: 'rgba(255,255,255,0.55)',
          }}
        >
          <span>9:41</span>
          <div
            style={{
              position: 'absolute',
              left: '50%',
              top: 10,
              transform: 'translateX(-50%)',
              width: 96,
              height: 24,
              borderRadius: 12,
              background: '#05060A',
            }}
          />
          <span>5G ▮</span>
        </div>
        <div style={{ position: 'absolute', inset: '44px 0 0 0' }}>{children}</div>
      </div>
    </div>
  );
};

/* ============ 5. 对话气泡 ============ */

export const ChatBubble: React.FC<{
  role: 'user' | 'ai';
  text: string;
  from?: number;
  accent?: string;
  maxWidth?: number;
  fontSize?: number;
}> = ({ role, text, from = 0, accent = C.cyan, maxWidth = 280, fontSize = 17 }) => {
  const frame = useCurrentFrame();
  const p = enter(frame, from, 12);
  const isUser = role === 'user';

  return (
    <div
      style={{
        display: 'flex',
        justifyContent: isUser ? 'flex-end' : 'flex-start',
        opacity: p,
        transform: `translateY(${(1 - p) * 16}px) scale(${0.96 + p * 0.04})`,
      }}
    >
      <div
        style={{
          maxWidth,
          padding: '12px 16px',
          borderRadius: isUser ? '18px 18px 4px 18px' : '18px 18px 18px 4px',
          background: isUser
            ? `linear-gradient(135deg, ${accent}E6, ${C.violet}CC)`
            : 'rgba(255,255,255,0.07)',
          border: isUser ? 'none' : `1px solid ${C.line}`,
          fontFamily: F.cn,
          fontSize,
          lineHeight: 1.55,
          color: isUser ? '#04121A' : 'rgba(255,255,255,0.9)',
          fontWeight: isUser ? 600 : 400,
        }}
      >
        {text}
      </div>
    </div>
  );
};

/* ============ 6. 结论条 ============ */

export const ResultBar: React.FC<{
  label: string;
  value: string;
  from?: number;
  accent?: string;
  width?: number;
}> = ({ label, value, from = 0, accent = C.lime, width = 900 }) => {
  const frame = useCurrentFrame();
  const p = enter(frame, from, 16);
  return (
    <div
      style={{
        width,
        display: 'flex',
        alignItems: 'center',
        gap: 18,
        padding: '16px 24px',
        borderRadius: 14,
        border: `1px solid ${accent}55`,
        background: `${accent}10`,
        boxShadow: `0 0 60px ${accent}22`,
        opacity: p,
        transform: `translateY(${(1 - p) * 18}px)`,
      }}
    >
      <span
        style={{
          fontFamily: F.mono,
          fontSize: 18,
          letterSpacing: 2,
          color: accent,
          fontWeight: 700,
          flexShrink: 0,
        }}
      >
        {label}
      </span>
      <span
        style={{
          fontFamily: F.cn,
          fontSize: 26,
          fontWeight: 600,
          color: C.ink,
        }}
      >
        {value}
      </span>
    </div>
  );
};

/* ============ 7. 左侧竖排章节序号 ============ */

export const SceneIndex: React.FC<{ n: string; from?: number; accent?: string }> = ({
  n,
  from = 0,
  accent = C.cyan,
}) => {
  const frame = useCurrentFrame();
  const p = enter(frame, from, 20);
  return (
    <div
      style={{
        position: 'absolute',
        right: 96,
        top: 92,
        fontFamily: F.mono,
        fontSize: 150,
        fontWeight: 900,
        lineHeight: 1,
        color: 'transparent',
        WebkitTextStroke: `1.5px ${accent}40`,
        opacity: p * 0.9,
      }}
    >
      {n}
    </div>
  );
};
