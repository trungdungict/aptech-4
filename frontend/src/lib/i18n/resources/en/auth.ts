export const enAuth = {
  login: {
    title: 'Sign in',
    subtitle: 'Use your Aptech-4 customer account. Demo credentials come from seed, not this form.',
    email: 'Email',
    password: 'Password',
    submit: 'Sign in',
    submitting: 'Signing in…',
    noAccount: 'No account yet?',
    createOne: 'Create one',
    unknownError: 'Unable to sign in. Please try again.',
  },
  register: {
    title: 'Create account',
    subtitle: 'Register a customer account. CUSTOMER role is assigned by the server — not chosen here.',
    email: 'Email',
    fullName: 'Full name',
    password: 'Password',
    submit: 'Create account',
    submitting: 'Creating account…',
    hasAccount: 'Already have an account?',
    signIn: 'Sign in',
    unknownError: 'Unable to register. Please try again.',
  },
  requireAuth: {
    checking: 'Checking session…',
  },
} as const
