import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import '../../core/constants/app_colors.dart';
import '../bloc/auth/auth_bloc.dart';
import '../bloc/auth/auth_event.dart';
import '../bloc/auth/auth_state.dart';
import '../common/app_button.dart';
import '../common/app_text_field.dart';
import '../common/custom_alert.dart';

class AuthScreen extends StatefulWidget {
  const AuthScreen({super.key});

  @override
  State<AuthScreen> createState() => _AuthScreenState();
}

class _AuthScreenState extends State<AuthScreen> with SingleTickerProviderStateMixin {
  late TabController _tabController;

  // Login Controllers
  final _loginUsernameController = TextEditingController();
  final _loginPasswordController = TextEditingController();

  // Register Controllers
  final _regUsernameController = TextEditingController();
  final _regEmailController = TextEditingController();
  final _regPasswordController = TextEditingController();

  // OTP Verification Controller
  final _otpCodeController = TextEditingController();
  String? _pendingVerificationEmail;

  final _formKeyLogin = GlobalKey<FormState>();
  final _formKeyRegister = GlobalKey<FormState>();
  final _formKeyOtp = GlobalKey<FormState>();

  bool _obscureLoginPassword = true;
  bool _obscureRegPassword = true;

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 2, vsync: this);
  }

  @override
  void dispose() {
    _tabController.dispose();
    _loginUsernameController.dispose();
    _loginPasswordController.dispose();
    _regUsernameController.dispose();
    _regEmailController.dispose();
    _regPasswordController.dispose();
    _otpCodeController.dispose();
    super.dispose();
  }

  void _showForgotPasswordDialog() {
    final emailController = TextEditingController();
    final formKey = GlobalKey<FormState>();

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) => Container(
        padding: EdgeInsets.only(
          bottom: MediaQuery.of(ctx).viewInsets.bottom + 24,
          top: 24,
          left: 20,
          right: 20,
        ),
        decoration: const BoxDecoration(
          color: AppColors.bgSurface,
          borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
        ),
        child: Form(
          key: formKey,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  const Text(
                    'Reset Password',
                    style: TextStyle(fontSize: 18, fontWeight: FontWeight.w700),
                  ),
                  IconButton(
                    icon: const Icon(Icons.close, size: 20),
                    onPressed: () => Navigator.pop(ctx),
                  ),
                ],
              ),
              const SizedBox(height: 8),
              const Text(
                'Enter your account email to receive a password reset link.',
                style: TextStyle(fontSize: 13, color: AppColors.textSecondary),
              ),
              const SizedBox(height: 16),
              AppTextField(
                controller: emailController,
                label: 'Email',
                hint: 'name@example.com',
                keyboardType: TextInputType.emailAddress,
                prefixIcon: const Icon(Icons.email_outlined, size: 18),
                validator: (val) {
                  if (val == null || !val.contains('@')) return 'Enter a valid email';
                  return null;
                },
              ),
              const SizedBox(height: 20),
              AppButton(
                text: 'Send Reset Link',
                onPressed: () {
                  if (formKey.currentState?.validate() == true) {
                    context.read<AuthBloc>().add(
                          ForgotPasswordSubmittedEvent(email: emailController.text),
                        );
                    Navigator.pop(ctx);
                  }
                },
              ),
            ],
          ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return BlocConsumer<AuthBloc, AuthState>(
      listener: (context, state) {
        if (state is AuthVerificationRequiredState) {
          setState(() {
            _pendingVerificationEmail = state.email;
          });
        }
      },
      builder: (context, state) {
        final isLoading = state is AuthLoadingState;
        final errorMessage = state is AuthErrorState ? state.message : null;
        final infoMessage = state is AuthMessageState
            ? state.message
            : state is AuthVerificationRequiredState
                ? state.infoMessage
                : null;

        return Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 24),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 440),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  // App Logo & Header
                  Container(
                    width: 52,
                    height: 52,
                    decoration: BoxDecoration(
                      color: AppColors.primaryLight,
                      borderRadius: BorderRadius.circular(14),
                      border: Border.all(color: AppColors.primaryBorder),
                    ),
                    child: const Icon(Icons.shield_outlined, size: 28, color: AppColors.primary),
                  ),
                  const SizedBox(height: 14),
                  const Text(
                    'SuperApp KYC',
                    style: TextStyle(
                      fontSize: 22,
                      fontWeight: FontWeight.w800,
                      color: AppColors.textPrimary,
                      letterSpacing: -0.5,
                    ),
                  ),
                  const SizedBox(height: 4),
                  const Text(
                    'Secure Identity Verification Portal',
                    style: TextStyle(
                      fontSize: 13,
                      color: AppColors.textSecondary,
                    ),
                  ),
                  const SizedBox(height: 24),

                  // Notifications Banner
                  if (errorMessage != null) ...[
                    CustomAlert(message: errorMessage, type: AlertType.error),
                    const SizedBox(height: 16),
                  ],
                  if (infoMessage != null) ...[
                    CustomAlert(message: infoMessage, type: AlertType.info),
                    const SizedBox(height: 16),
                  ],

                  // Card Content: OTP Verification or Tabbed Auth
                  Container(
                    decoration: BoxDecoration(
                      color: AppColors.bgSurface,
                      borderRadius: BorderRadius.circular(16),
                      border: Border.all(color: AppColors.border),
                      boxShadow: const [
                        BoxShadow(
                          color: Color(0x0A0F172A),
                          blurRadius: 16,
                          offset: Offset(0, 4),
                        )
                      ],
                    ),
                    padding: const EdgeInsets.all(20),
                    child: _pendingVerificationEmail != null
                        ? _buildOtpVerificationView(isLoading)
                        : _buildTabbedAuthView(isLoading),
                  ),
                ],
              ),
            ),
          ),
        );
      },
    );
  }

  Widget _buildOtpVerificationView(bool isLoading) {
    return Form(
      key: _formKeyOtp,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              IconButton(
                icon: const Icon(Icons.arrow_back, size: 20),
                padding: EdgeInsets.zero,
                constraints: const BoxConstraints(),
                onPressed: () {
                  setState(() {
                    _pendingVerificationEmail = null;
                  });
                },
              ),
              const SizedBox(width: 8),
              const Text(
                'Verify Your Email',
                style: TextStyle(fontSize: 16, fontWeight: FontWeight.w700),
              ),
            ],
          ),
          const SizedBox(height: 10),
          Text(
            'We sent a 6-digit confirmation code to $_pendingVerificationEmail',
            style: const TextStyle(fontSize: 13, color: AppColors.textSecondary),
          ),
          const SizedBox(height: 18),
          AppTextField(
            controller: _otpCodeController,
            label: 'Verification Code',
            hint: '123456',
            keyboardType: TextInputType.number,
            maxLength: 6,
            prefixIcon: const Icon(Icons.key_outlined, size: 18),
            validator: (val) {
              if (val == null || val.length < 6) return 'Enter the complete 6-digit code';
              return null;
            },
          ),
          const SizedBox(height: 20),
          AppButton(
            text: 'Verify Account',
            isLoading: isLoading,
            onPressed: () {
              if (_formKeyOtp.currentState?.validate() == true) {
                context.read<AuthBloc>().add(
                      VerifyEmailSubmittedEvent(
                        email: _pendingVerificationEmail!,
                        code: _otpCodeController.text,
                      ),
                    );
              }
            },
          ),
          const SizedBox(height: 12),
          Center(
            child: TextButton(
              onPressed: () {
                context.read<AuthBloc>().add(
                      ResendVerificationSubmittedEvent(email: _pendingVerificationEmail!),
                    );
              },
              child: const Text(
                'Resend Code',
                style: TextStyle(color: AppColors.primary, fontWeight: FontWeight.w600, fontSize: 13),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildTabbedAuthView(bool isLoading) {
    return Column(
      children: [
        // Tab Selector
        Container(
          height: 42,
          decoration: BoxDecoration(
            color: const Color(0xFFF1F5F9),
            borderRadius: BorderRadius.circular(10),
          ),
          child: TabBar(
            controller: _tabController,
            indicator: BoxDecoration(
              color: AppColors.bgSurface,
              borderRadius: BorderRadius.circular(8),
              boxShadow: const [
                BoxShadow(color: Color(0x0F000000), blurRadius: 4, offset: Offset(0, 1))
              ],
            ),
            indicatorSize: TabBarIndicatorSize.tab,
            dividerColor: Colors.transparent,
            labelColor: AppColors.textPrimary,
            unselectedLabelColor: AppColors.textMuted,
            labelStyle: const TextStyle(fontWeight: FontWeight.w700, fontSize: 13),
            tabs: const [
              Tab(text: 'Sign In'),
              Tab(text: 'Create Account'),
            ],
          ),
        ),
        const SizedBox(height: 20),

        SizedBox(
          height: 300,
          child: TabBarView(
            controller: _tabController,
            children: [
              _buildLoginForm(isLoading),
              _buildRegisterForm(isLoading),
            ],
          ),
        ),
      ],
    );
  }

  Widget _buildLoginForm(bool isLoading) {
    return Form(
      key: _formKeyLogin,
      child: Column(
        children: [
          AppTextField(
            controller: _loginUsernameController,
            label: 'Username or Email',
            hint: 'johndoe',
            prefixIcon: const Icon(Icons.person_outline, size: 18),
            validator: (val) => val == null || val.isEmpty ? 'Field required' : null,
          ),
          const SizedBox(height: 12),
          AppTextField(
            controller: _loginPasswordController,
            label: 'Password',
            hint: '••••••••',
            obscureText: _obscureLoginPassword,
            prefixIcon: const Icon(Icons.lock_outline, size: 18),
            suffixIcon: IconButton(
              icon: Icon(
                _obscureLoginPassword ? Icons.visibility_off_outlined : Icons.visibility_outlined,
                size: 18,
              ),
              onPressed: () => setState(() => _obscureLoginPassword = !_obscureLoginPassword),
            ),
            validator: (val) => val == null || val.isEmpty ? 'Password required' : null,
          ),
          Align(
            alignment: Alignment.centerRight,
            child: TextButton(
              onPressed: _showForgotPasswordDialog,
              child: const Text(
                'Forgot password?',
                style: TextStyle(color: AppColors.primary, fontSize: 12, fontWeight: FontWeight.w600),
              ),
            ),
          ),
          const Spacer(),
          AppButton(
            text: 'Sign In',
            isLoading: isLoading,
            onPressed: () {
              if (_formKeyLogin.currentState?.validate() == true) {
                context.read<AuthBloc>().add(
                      LoginSubmittedEvent(
                        login: _loginUsernameController.text,
                        password: _loginPasswordController.text,
                      ),
                    );
              }
            },
          ),
        ],
      ),
    );
  }

  Widget _buildRegisterForm(bool isLoading) {
    return Form(
      key: _formKeyRegister,
      child: SingleChildScrollView(
        child: Column(
          children: [
            AppTextField(
              controller: _regUsernameController,
              label: 'Username',
              hint: 'alexsmith',
              prefixIcon: const Icon(Icons.person_outline, size: 18),
              validator: (val) => val == null || val.length < 3 ? 'At least 3 characters' : null,
            ),
            const SizedBox(height: 10),
            AppTextField(
              controller: _regEmailController,
              label: 'Email',
              hint: 'alex@example.com',
              keyboardType: TextInputType.emailAddress,
              prefixIcon: const Icon(Icons.email_outlined, size: 18),
              validator: (val) => val == null || !val.contains('@') ? 'Valid email required' : null,
            ),
            const SizedBox(height: 10),
            AppTextField(
              controller: _regPasswordController,
              label: 'Password',
              hint: '••••••••',
              obscureText: _obscureRegPassword,
              prefixIcon: const Icon(Icons.lock_outline, size: 18),
              suffixIcon: IconButton(
                icon: Icon(
                  _obscureRegPassword ? Icons.visibility_off_outlined : Icons.visibility_outlined,
                  size: 18,
                ),
                onPressed: () => setState(() => _obscureRegPassword = !_obscureRegPassword),
              ),
              validator: (val) => val == null || val.length < 6 ? 'At least 6 characters' : null,
            ),
            const SizedBox(height: 16),
            AppButton(
              text: 'Create Account',
              isLoading: isLoading,
              onPressed: () {
                if (_formKeyRegister.currentState?.validate() == true) {
                  context.read<AuthBloc>().add(
                        RegisterSubmittedEvent(
                          username: _regUsernameController.text,
                          email: _regEmailController.text,
                          password: _regPasswordController.text,
                        ),
                      );
                }
              },
            ),
          ],
        ),
      ),
    );
  }
}
