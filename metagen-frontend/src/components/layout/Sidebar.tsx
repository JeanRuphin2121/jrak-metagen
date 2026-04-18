import { NavLink, useNavigate } from 'react-router-dom'
import { LayoutDashboard, Database, Table2, Sparkles, Users, LogOut } from 'lucide-react'
import { logout, getCurrentUser, hasRole } from '@/lib/auth'
import clsx from 'clsx'

const navItems = [
  { to: '/dashboard',       label: 'Dashboard',    icon: LayoutDashboard },
  { to: '/studio/entities', label: 'Studio',        icon: Database },
  { to: '/ai',              label: 'AI Assistant',  icon: Sparkles },
]

const adminItems = [
  { to: '/admin/users', label: 'Users', icon: Users },
]

export default function Sidebar() {
  const navigate = useNavigate()
  const user     = getCurrentUser()
  const isAdmin  = hasRole('ADMIN')

  function handleLogout() {
    logout()
    navigate('/login', { replace: true })
  }

  return (
    <aside className="flex h-full w-56 flex-col bg-gray-900 text-white shrink-0">
      {/* Logo */}
      <div className="flex h-16 items-center gap-2 px-5 border-b border-gray-700">
        <span className="text-brand-500 font-bold text-xl">Metagen</span>
        <span className="text-xs text-gray-400 mt-0.5">Studio</span>
      </div>

      {/* Nav */}
      <nav className="flex-1 overflow-y-auto py-4 px-3 space-y-1">
        {navItems.map(({ to, label, icon: Icon }) => (
          <NavLink key={to} to={to} className={navClass}>
            <Icon size={17} />
            {label}
          </NavLink>
        ))}

        {isAdmin && (
          <>
            <div className="pt-4 pb-1 px-2">
              <p className="text-xs font-semibold uppercase tracking-wider text-gray-500">Admin</p>
            </div>
            {adminItems.map(({ to, label, icon: Icon }) => (
              <NavLink key={to} to={to} className={navClass}>
                <Icon size={17} />
                {label}
              </NavLink>
            ))}
          </>
        )}
      </nav>

      {/* User + logout */}
      <div className="border-t border-gray-700 px-4 py-3">
        <p className="text-xs text-gray-400 truncate mb-2">{user?.username}</p>
        <button
          onClick={handleLogout}
          className="flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-sm text-gray-300 hover:bg-gray-800 hover:text-white transition-colors"
        >
          <LogOut size={15} />
          Logout
        </button>
      </div>
    </aside>
  )
}

function navClass({ isActive }: { isActive: boolean }) {
  return clsx(
    'flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors',
    isActive
      ? 'bg-brand-700 text-white'
      : 'text-gray-300 hover:bg-gray-800 hover:text-white',
  )
}
