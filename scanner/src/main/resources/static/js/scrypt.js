{
    const headerLink = document.getElementById('login');
    const authPopup = document.getElementById('auth-popup')
    const authPopupClose = document.getElementById('auth-popup-close');
    const popupInfoHeader = document.querySelector('.popup-info__header');
    const signUpForm = document.getElementById('sign-up-form');
    const signInForm = document.getElementById('sign-in-form');
    const authBlockText = document.querySelector('.authentication-block__text');
    const authFormLoginBtn = document.getElementById('auth-form-login-btn');
    const authBlockLoginBtnText = document.querySelector('.authentication-block__login-btn-text');
    const notification = document.getElementById('notification');

    // Управление показом модального окна
    headerLink.addEventListener('click', togglePopup);
    authPopupClose.addEventListener('click', closePopup);
    authFormLoginBtn.addEventListener('click', (e) => {
        e.preventDefault();
        if (signUpForm.dataset.visible === 'true') {
            toggleForms(signInForm, signUpForm);
        } else {
            toggleForms(signUpForm, signInForm);
        }
    });

    // Обработка форм
    signUpForm.addEventListener('submit', submitSignUpForm);
    signInForm.addEventListener('submit', submitSignInForm);

    function togglePopup(e) {
        e.preventDefault();
        authPopup.classList.toggle('element-active');
        authPopup.style.zIndex = authPopup.classList.contains('element-active') ? 100 : -100;
    }

    function closePopup(e) {
        e.preventDefault();
        authPopup.classList.remove('element-active');
        changeZIndex(-100, authPopup);
        userData = null;
    }

    function toggleForms(form, hideForm1) {
        setFormVisibility(form, 'visible');
        setFormVisibility(hideForm1, 'notVisible');
    }

    function setFormVisibility(form, visible) {
        if (visible === 'visible') {
            form.classList.add('element-active');
            form.dataset.visible = 'true';
            updateFormTexts(form);
        }
        if (visible === 'notVisible') {
            form.classList.remove('element-active');
            form.dataset.visible = 'false';
        }
    }

    function setNotificationVisibility(data, visible, isError) {
        if (visible === 'show') {
            notification.classList.add('element-active');
            notification.dataset.visible = true;
            if (isError) {
                notification.textContent = data.message;
            }
        }
        if (visible === 'hide') {
            notification.classList.remove('element-active');
            notification.dataset.visible = false;
        }
    }

    function updateFormTexts(form) {
        const isSignInForm = form === signInForm;
        popupInfoHeader.textContent = isSignInForm ? 'Авторизация' : 'Регистрация';
        authBlockText.textContent = isSignInForm ? 'Еще нет аккаунта?' : 'Уже есть аккаунт?';
        authBlockLoginBtnText.textContent = isSignInForm ? 'Зарегистрироваться' : 'Авторизоваться';
    }

    function changeZIndex(value, element) {
        element.style.zIndex = value;
    }

    async function submitSignUpForm(e) {
        e.preventDefault();
        userPassword = document.querySelector('[name=password-reg]').value,
        userConfirmPassword = document.querySelector('[name=confirm-password]').value

        const regRequest = {
            username: document.querySelector('[name=username-reg]').value,
            password: document.querySelector('[name=password-reg]').value,
            roles: ['ROLE_USER']
        };

        try {
            const response = await fetch('/api/v1/auth/sign-up', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify(regRequest)
            });
            const data = await response.json();
            setNotificationVisibility(data, 'show', false)
            console.log(data);
        } catch (error) {
            setNotificationVisibility(error, 'show', true)
            console.error('Ошибка при регистрации:', error);
        }
        setTimeout(() => setNotificationVisibility('', 'hide', false), 3000)
        signUpForm.reset();
    }

    async function submitSignInForm(e) {
        e.preventDefault();
        const signInRequest = {
            username: document.querySelector('[name=username-auth]').value,
            password: document.querySelector('[name=password-auth]').value
        };

        try {
            const response = await fetch('/api/v1/auth/sign-in', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify(signInRequest)
            });
            const data = await response.json();
            console.log('Вход выполнен успешно:', data);
//            window.location.href = '/dashboard';
        } catch (error) {
            console.error('Ошибка при входе:', error);
        }
        submitSignInForm.reset();
    }
}