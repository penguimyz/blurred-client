import { Icon } from "./Icon";
import { Avatar } from "./Avatar";
import { useAccountStore } from "../store/accountStore";
import { useChatStore } from "../store/chatStore";
import { NAV, PRIMARY, UTILITY, type NavItem, type NavKey } from "../lib/nav";

/**
 * The navigation rail. Icons only, with a tooltip that slides out on hover
 * (.rail-btn::after in theme.css) — the Lunar shape, which buys the content
 * area ~130px over a labelled sidebar.
 *
 * Labels come from `lib/nav.ts`, which the pages also read for their headings,
 * so the rail and the screen it opens can't disagree about what a tab is called.
 */
export function Sidebar({
  active,
  onSelect,
}: {
  active: string;
  onSelect: (key: NavKey) => void;
}) {
  const accounts = useAccountStore((s) => s.accounts);
  const unread = useChatStore((s) => s.totalUnread());

  // The active account is the most recently used one — same rule the backend
  // uses to pick an account at launch, so the face here always matches the
  // account the Play button will actually use.
  const activeAccount = accounts
    .slice()
    .sort((a, b) => (b.lastUsed ?? "").localeCompare(a.lastUsed ?? ""))[0];

  return (
    <nav className="rail" aria-label="Main">
      {PRIMARY.map((key) => (
        <RailButton
          key={key}
          item={NAV[key]}
          active={active === key}
          onSelect={onSelect}
          pip={key === "chat" && unread > 0}
        />
      ))}

      <div className="rail-spacer" />

      {UTILITY.map((key) => (
        <RailButton key={key} item={NAV[key]} active={active === key} onSelect={onSelect} />
      ))}

      <div className="rail-divider" />

      {/* Account tile doubles as the Accounts nav entry. */}
      <button
        className={`rail-btn ${active === "accounts" ? "active" : ""}`}
        data-label={activeAccount ? activeAccount.username : NAV.accounts.label}
        onClick={() => onSelect("accounts")}
        aria-label={NAV.accounts.label}
        aria-current={active === "accounts" ? "page" : undefined}
      >
        {activeAccount ? (
          <Avatar account={activeAccount} size={26} />
        ) : (
          <Icon name={NAV.accounts.icon} size={20} />
        )}
      </button>
    </nav>
  );
}

function RailButton({
  item,
  active,
  onSelect,
  pip,
}: {
  item: NavItem;
  active: boolean;
  onSelect: (key: NavKey) => void;
  pip?: boolean;
}) {
  return (
    <button
      className={`rail-btn ${active ? "active" : ""}`}
      data-label={item.label}
      onClick={() => onSelect(item.key)}
      aria-label={item.label}
      aria-current={active ? "page" : undefined}
    >
      <Icon name={item.icon} size={20} />
      {pip && <span className="rail-pip" />}
    </button>
  );
}
