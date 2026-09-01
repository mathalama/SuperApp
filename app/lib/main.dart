import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'core/di/injection_container.dart';
import 'core/theme/app_theme.dart';
import 'domain/repositories/i_auth_repository.dart';
import 'domain/repositories/i_kyc_repository.dart';
import 'presentation/auth/auth_screen.dart';
import 'presentation/bloc/auth/auth_bloc.dart';
import 'presentation/bloc/auth/auth_event.dart';
import 'presentation/bloc/auth/auth_state.dart';
import 'presentation/bloc/kyc/kyc_bloc.dart';
import 'presentation/home_kyc_flow_screen.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // Initialize DI service locator container
  await initDependencies(
    onSessionExpired: () {
      debugPrint('[SuperApp] Global session expired -> logging out');
    },
  );

  runApp(const SuperAppKycApp());
}

class SuperAppKycApp extends StatelessWidget {
  const SuperAppKycApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MultiBlocProvider(
      providers: [
        BlocProvider<AuthBloc>(
          create: (_) => AuthBloc(
            authRepository: sl<IAuthRepository>(),
          )..add(CheckAuthStatusEvent()),
        ),
        BlocProvider<KycBloc>(
          create: (_) => KycBloc(
            kycRepository: sl<IKycRepository>(),
          ),
        ),
      ],
      child: MaterialApp(
        title: 'SuperApp KYC',
        debugShowCheckedModeBanner: false,
        theme: AppTheme.lightTheme,
        home: const RootNavigationCoordinator(),
      ),
    );
  }
}

class RootNavigationCoordinator extends StatelessWidget {
  const RootNavigationCoordinator({super.key});

  @override
  Widget build(BuildContext context) {
    return BlocBuilder<AuthBloc, AuthState>(
      builder: (context, state) {
        if (state is AuthAuthenticatedState) {
          return HomeKycFlowScreen(user: state.user);
        }

        if (state is AuthInitialState) {
          return const Scaffold(
            body: Center(
              child: CircularProgressIndicator(),
            ),
          );
        }

        // Unauthenticated, loading, error, verification states render the AuthScreen
        return const Scaffold(
          body: SafeArea(
            child: AuthScreen(),
          ),
        );
      },
    );
  }
}
