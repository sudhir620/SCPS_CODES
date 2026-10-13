function showLoader(title = 'Loading...', text = 'Please wait! While we process your request!') {
  Swal.fire({
    title: title,
    text: text,
    allowOutsideClick: false,
    allowEscapeKey: false,
    didOpen: () => {
      Swal.showLoading();
    }
  });
}

function closeLoader() {
  if (Swal.isVisible()) {
    Swal.close();
  }
}