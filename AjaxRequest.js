var form = $('#form_chat');

  form.find('input[type=submit]').on( 'click', function(e) {
    e.prevetDefault();
    $.ajax( {
      type: "POST",
      url: form.attr( 'action' ),
      data: form.serialize(),
      success: function( response ) {
        console.log( response );
      }
    } );
