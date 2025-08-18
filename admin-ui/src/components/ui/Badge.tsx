import React from 'react'
import { cn } from '../../lib/utils'

interface BadgeProps extends React.HTMLAttributes<HTMLDivElement> {
  variant?: 'default' | 'secondary' | 'destructive' | 'success' | 'warning' | 'outline'
  children: React.ReactNode
}

const badgeVariants = {
  default: 'border-transparent bg-blue-500 text-white hover:bg-blue-600',
  secondary: 'border-transparent bg-gray-100 text-gray-900 hover:bg-gray-200',
  destructive: 'border-transparent bg-red-500 text-white hover:bg-red-600',
  success: 'border-transparent bg-green-500 text-white hover:bg-green-600',
  warning: 'border-transparent bg-yellow-500 text-white hover:bg-yellow-600',
  outline: 'text-gray-900 border-current',
}

/**
 * Badge component for displaying status indicators and labels
 * 
 * @example
 * ```tsx
 * <Badge variant="success">Completed</Badge>
 * <Badge variant="destructive">Error</Badge>
 * ```
 */
export const Badge = React.forwardRef<HTMLDivElement, BadgeProps>(
  ({ className, variant = 'default', children, ...props }, ref) => {
    return (
      <div
        ref={ref}
        className={cn(
          'inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-semibold transition-colors focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2',
          badgeVariants[variant],
          className
        )}
        {...props}
      >
        {children}
      </div>
    )
  }
)
Badge.displayName = 'Badge'