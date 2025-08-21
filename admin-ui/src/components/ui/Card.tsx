import React from 'react'
import { cn } from '../../lib/utils'

interface CardProps extends React.HTMLAttributes<HTMLDivElement> {
  children: React.ReactNode
}

interface CardHeaderProps extends React.HTMLAttributes<HTMLDivElement> {
  children: React.ReactNode
}

interface CardTitleProps extends React.HTMLAttributes<HTMLHeadingElement> {
  children: React.ReactNode
}

interface CardContentProps extends React.HTMLAttributes<HTMLDivElement> {
  children: React.ReactNode
}

interface CardFooterProps extends React.HTMLAttributes<HTMLDivElement> {
  children: React.ReactNode
}

/**
 * Card component for grouping related content
 */
export const Card = React.forwardRef<HTMLDivElement, CardProps>(
  ({ className, children, ...props }, ref) => (
    <div
      ref={ref}
      className={cn(
        'rounded-lg border border-gray-200 bg-white text-gray-900 shadow-sm',
        className
      )}
      {...props}
    >
      {children}
    </div>
  )
)
Card.displayName = 'Card'

/**
 * Card header component
 */
export const CardHeader = React.forwardRef<HTMLDivElement, CardHeaderProps>(
  ({ className, children, ...props }, ref) => (
    <div ref={ref} className={cn('flex flex-col space-y-1.5 p-6', className)} {...props}>
      {children}
    </div>
  )
)
CardHeader.displayName = 'CardHeader'

/**
 * Card title component
 */
export const CardTitle = React.forwardRef<HTMLHeadingElement, CardTitleProps>(
  ({ className, children, ...props }, ref) => (
    <h3
      ref={ref}
      className={cn('text-2xl font-semibold leading-none tracking-tight', className)}
      {...props}
    >
      {children}
    </h3>
  )
)
CardTitle.displayName = 'CardTitle'

/**
 * Card content component
 */
export const CardContent = React.forwardRef<HTMLDivElement, CardContentProps>(
  ({ className, children, ...props }, ref) => (
    <div ref={ref} className={cn('p-6 pt-0', className)} {...props}>
      {children}
    </div>
  )
)
CardContent.displayName = 'CardContent'

/**
 * Card footer component
 */
export const CardFooter = React.forwardRef<HTMLDivElement, CardFooterProps>(
  ({ className, children, ...props }, ref) => (
    <div ref={ref} className={cn('flex items-center p-6 pt-0', className)} {...props}>
      {children}
    </div>
  )
)
CardFooter.displayName = 'CardFooter'