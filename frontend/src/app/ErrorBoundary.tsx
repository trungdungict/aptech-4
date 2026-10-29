import { Component, type ErrorInfo, type ReactNode } from 'react'

type Props = {
  children: ReactNode
}

type State = {
  hasError: boolean
  message: string
}

export class ErrorBoundary extends Component<Props, State> {
  state: State = { hasError: false, message: '' }

  static getDerivedStateFromError(error: Error): State {
    return { hasError: true, message: error.message || 'Unexpected error' }
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    console.error('UI error boundary caught', error, info)
  }

  render() {
    if (this.state.hasError) {
      return (
        <main className="mx-auto flex min-h-screen max-w-lg flex-col justify-center gap-3 px-6 text-foreground">
          <h1 className="text-2xl font-semibold">Something went wrong</h1>
          <p className="text-muted-foreground">{this.state.message}</p>
          <button
            type="button"
            className="w-fit rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground"
            onClick={() => this.setState({ hasError: false, message: '' })}
          >
            Try again
          </button>
        </main>
      )
    }

    return this.props.children
  }
}
