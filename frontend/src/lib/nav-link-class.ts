import { cn } from '@/lib/utils'

/** Shared active/inactive styles for Customer and Admin text nav links. */
export function navLinkClass(isActive: boolean, className?: string): string {
  return cn(
    'rounded-lg px-3 py-2 text-sm font-medium transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary',
    isActive
      ? 'bg-primary text-primary-foreground'
      : 'text-foreground hover:bg-muted',
    className,
  )
}
